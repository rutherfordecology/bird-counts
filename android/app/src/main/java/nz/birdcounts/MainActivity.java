package nz.birdcounts;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.webkit.ServiceWorkerClientCompat;
import androidx.webkit.ServiceWorkerControllerCompat;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewFeature;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * A shell around the Bird Count Field page (../field), on the same pattern as
 * Site Assessment and Forest Monitor, plus screen-off alerts.
 *
 * WebViewAssetLoader serves the bundled page from
 * https://appassets.androidplatform.net/app/ - a real origin with no server,
 * so geolocation, localStorage, the Cache API and the service worker (which
 * serves the offline map tiles) all work as they do in Chrome.
 *
 * Leaflet is bundled too: requests for unpkg.com/leaflet@1.9.4 are answered
 * from assets, so the map works on first open with no signal.
 *
 * The page tells the app what to watch for through {@link Bridge#update}:
 * points not yet counted today, the alert radius, and when a running count
 * ends. {@link AlertService} keeps GPS on and alerts with the screen off.
 */
public class MainActivity extends android.app.Activity {

    static final String ORIGIN = "https://appassets.androidplatform.net";
    private static final String PAGE = ORIGIN + "/app/index.html";
    private static final String LEAFLET = "https://unpkg.com/leaflet@1.9.4/dist/";

    static final String PREFS = "birdcounts";
    static final String PREF_STATE = "state";

    private static final int REQ_PERMS = 1;
    private static final int REQ_FILE_CHOOSER = 2;

    /** True while the app is on screen. The service stays quiet then: the page alerts itself. */
    static volatile boolean visible = false;

    private WebView web;
    private boolean pageReady = false;
    private ValueCallback<Uri[]> fileChooserCallback;
    private WebViewAssetLoader loader;

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);

        web = new WebView(this);
        setContentView(web);

        loader = new WebViewAssetLoader.Builder()
                .setDomain("appassets.androidplatform.net")
                .addPathHandler("/app/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return intercept(request.getUrl());
            }

            // Anything that is not our own page goes to a real browser.
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if (ORIGIN.equals(url.getScheme() + "://" + url.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, url));
                } catch (ActivityNotFoundException ignored) { }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                deliverArrival();
            }
        });

        // The service worker's own requests (its install list, offline tiles)
        // need the same answers as the page's.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE)
                && WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_SHOULD_INTERCEPT_REQUEST)) {
            ServiceWorkerControllerCompat.getInstance().setServiceWorkerClient(new ServiceWorkerClientCompat() {
                @Override
                public WebResourceResponse shouldInterceptRequest(@NonNull WebResourceRequest request) {
                    return intercept(request.getUrl());
                }
            });
        }

        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);     // localStorage, where the counts live
        settings.setGeolocationEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);    // the file chooser returns content:// URIs
        settings.setMediaPlaybackRequiresUserGesture(false);   // the arrival beep

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                // Our own page in our own app; Android's permission is the real gate.
                cb.invoke(origin, true, true);
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
                fileChooserCallback = callback;
                // Not params.createIntent(): WebView maps ".geojson,.gpx" to MIME
                // types badly enough that nothing may be selectable. The page
                // checks what it is given anyway.
                Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(pick, "Choose a file"), REQ_FILE_CHOOSER);
                } catch (ActivityNotFoundException e) {
                    fileChooserCallback = null;
                    Toast.makeText(MainActivity.this, "No file manager on this phone.", Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            }
        });

        web.addJavascriptInterface(new Bridge(), "BirdHost");
        askPermissions();
        web.loadUrl(PAGE);
    }

    /* ------------------------------------------------------------- requests */

    @Nullable
    private WebResourceResponse intercept(Uri url) {
        String s = url.toString();
        if (s.startsWith(LEAFLET)) {
            String rest = s.substring(LEAFLET.length());
            int q = rest.indexOf('?');
            if (q >= 0) rest = rest.substring(0, q);
            if (rest.contains("..")) return null;
            try {
                InputStream in = getAssets().open("vendor/leaflet/" + rest);
                WebResourceResponse r = new WebResourceResponse(mimeOf(rest), null, in);
                // The service worker caches these with CORS requests.
                Map<String, String> h = new HashMap<>();
                h.put("Access-Control-Allow-Origin", "*");
                h.put("Cache-Control", "max-age=31536000");
                r.setResponseHeaders(h);
                return r;
            } catch (IOException e) {
                return null;     // not bundled: let it go to the network
            }
        }
        return loader.shouldInterceptRequest(url);
    }

    private static String mimeOf(String path) {
        if (path.endsWith(".js")) return "application/javascript";
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    /* ---------------------------------------------------------- lifecycle */

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        syncService();
        if (pageReady) deliverArrival();
    }

    @Override
    protected void onPause() {
        visible = false;
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    /** If the service alerted while the app was off screen, show that point's banner now (no second buzz). */
    private void deliverArrival() {
        String id = AlertService.takeLastArrival();
        if (id == null) return;
        pushToPage("window.BirdArrived && window.BirdArrived(" + JSONObject.quote(id) + ")");
    }

    /* ---------------------------------------------------------- the service */

    /**
     * Starts, updates or stops the alert service to match what the page last
     * asked for. A location service may only be started while the app is on
     * screen; once running it can be updated from anywhere.
     */
    void syncService() {
        boolean want = AlertService.wanted(this);
        boolean located = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        Intent i = new Intent(this, AlertService.class);
        try {
            if (want && located) {
                if (AlertService.running) startService(i);
                else if (visible) startForegroundService(i);
            } else if (AlertService.running) {
                stopService(i);
            }
        } catch (Exception e) {
            // Android refused (for instance, started from the background). It
            // will be tried again the next time the app comes on screen.
        }
    }

    private void askPermissions() {
        java.util.List<String> need = new java.util.ArrayList<>();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION);
            need.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), REQ_PERMS);
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] results) {
        if (code != REQ_PERMS) return;
        syncService();
        pushToPage("window.BirdStatus && window.BirdStatus()");
    }

    /* -------------------------------------------------------------- the page */

    /** Called from the page. Every method here runs on a binder thread. */
    private class Bridge {

        /** What to watch for, as JSON: see toHost() in field/index.html. */
        @JavascriptInterface
        public void update(String json) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_STATE, json).apply();
            runOnUiThread(MainActivity.this::syncService);
        }

        /** "on", "off", "no-location" or "no-notifications", for the Setup screen. */
        @JavascriptInterface
        public String alertStatus() {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                return "no-location";
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                return "no-notifications";
            return AlertService.running ? "on" : "off";
        }

        @JavascriptInterface
        public void openAppSettings() {
            runOnUiThread(() -> {
                try {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", getPackageName(), null)));
                } catch (ActivityNotFoundException ignored) { }
            });
        }

        /** Comma-separated milliseconds, off/on/off/on..., as navigator.vibrate takes. */
        @JavascriptInterface
        public void vibrate(String pattern) {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null) return;
            try {
                String[] parts = pattern.split(",");
                long[] ms = new long[parts.length + 1];
                ms[0] = 0;
                for (int i = 0; i < parts.length; i++) ms[i + 1] = Long.parseLong(parts[i].trim());
                v.vibrate(VibrationEffect.createWaveform(ms, -1));
            } catch (Exception ignored) { }
        }

        @JavascriptInterface
        public void keepScreenOn(final boolean on) {
            runOnUiThread(() -> {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        /** Asks Save to Downloads or Share; the page hears window.BirdSaved(true|false). */
        @JavascriptInterface
        public void saveFile(final String name, final String content, final String mime) {
            runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this)
                    .setTitle(safeName(name))
                    .setMessage("Keep a copy on the phone, or send it somewhere now?")
                    .setPositiveButton("Save to Downloads", (d, w) -> saved(saveToDownloads(name, content, mime)))
                    .setNegativeButton("Share", (d, w) -> saved(shareOut(name, content, mime)))
                    .setNeutralButton("Cancel", (d, w) -> saved(false))
                    .setOnCancelListener(d -> saved(false))
                    .show());
        }

        /** Keys baked in at build time from android/secrets.properties; blank when not set. */
        @JavascriptInterface
        public String secrets() {
            try {
                return new JSONObject()
                        .put("linz", BuildConfig.LINZ_KEY)
                        .put("githubToken", BuildConfig.GITHUB_TOKEN)
                        .put("githubRepo", BuildConfig.GITHUB_REPO)
                        .toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public String appBuild() {
            try {
                return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (Exception e) {
                return "";
            }
        }
    }

    private void pushToPage(final String js) {
        runOnUiThread(() -> { if (web != null) web.evaluateJavascript(js, null); });
    }

    private void saved(boolean ok) {
        pushToPage("window.BirdSaved && window.BirdSaved(" + ok + ")");
    }

    /** Writes into Downloads, where it survives the app being uninstalled. Works with no signal. */
    private boolean saveToDownloads(String name, String content, String mime) {
        final String filename = safeName(name);
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
            values.put(MediaStore.Downloads.MIME_TYPE, mime == null || mime.isEmpty() ? "text/plain" : mime);
            values.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri item = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (item == null) throw new IOException("Downloads folder refused the file");
            try (OutputStream os = getContentResolver().openOutputStream(item)) {
                if (os == null) throw new IOException("could not open the file for writing");
                os.write(content.getBytes(StandardCharsets.UTF_8));
            }
            values.clear();
            values.put(MediaStore.Downloads.IS_PENDING, 0);
            getContentResolver().update(item, values, null, null);
            Toast.makeText(this, "Saved to Downloads as " + filename, Toast.LENGTH_LONG).show();
            return true;
        } catch (Exception e) {
            Toast.makeText(this, "Could not save: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private boolean shareOut(String name, String content, String mime) {
        try {
            File dir = new File(getFilesDir(), "exports");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("could not create exports dir");
            File out = new File(dir, safeName(name));
            try (OutputStream os = new FileOutputStream(out)) {
                os.write(content.getBytes(StandardCharsets.UTF_8));
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", out);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType(mime == null || mime.isEmpty() ? "text/plain" : mime);
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.putExtra(Intent.EXTRA_SUBJECT, name);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Send " + name));
            return true;
        } catch (Exception e) {
            Toast.makeText(this, "Could not share " + name + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private static String safeName(String name) {
        String cleaned = name == null ? "" : name.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isEmpty() ? "export.txt" : cleaned;
    }

    @Override
    protected void onActivityResult(int request, int result, @Nullable Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQ_FILE_CHOOSER || fileChooserCallback == null) return;
        // Always answer, cancellation included, or the page's input never works again.
        Uri picked = (result == RESULT_OK && data != null) ? data.getData() : null;
        fileChooserCallback.onReceiveValue(picked == null ? null : new Uri[]{picked});
        fileChooserCallback = null;
    }

    /** Back closes a dialog or the count screen's sheets, then returns to the map, then leaves. */
    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.BirdBack ? window.BirdBack() : false", value -> {
            if (!"true".equals(value)) MainActivity.super.onBackPressed();
        });
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, MODE_PRIVATE);
    }
}

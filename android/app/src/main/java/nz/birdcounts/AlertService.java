package nz.birdcounts;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Keeps GPS on while you walk a loop, so the arrival alert and the end-of-count
 * alert work with the screen off or the phone in a pocket.
 *
 * It does what the page's checkArrival() does, on the list of points the page
 * last sent (those not yet counted today): within the alert radius of a point
 * it alerts once, and that point can alert again only after you have walked
 * more than twice the radius away. While the app is on screen it stays quiet,
 * because the page alerts itself then.
 *
 * It stops itself when there is nothing left to count today and no count is
 * running, when you tap Stop on its notification, or when you swipe the app
 * away. Opening the app starts it again if there is still something to do.
 */
public class AlertService extends Service implements LocationListener {

    static final String ACTION_STOP = "nz.birdcounts.STOP";

    private static final String CH_WATCH = "watch";
    private static final String CH_ALERT = "alert_sound_v1";
    private static final String CH_ALERT_QUIET = "alert_quiet_v1";
    private static final int ID_WATCH = 1;
    private static final int ID_ALERT = 2;
    private static final long[] BUZZ = {0, 400, 150, 400, 150, 400};
    /** Fixes worse than this are ignored: they jump about too much to call arrival on. */
    private static final float MAX_ACC_M = 75f;

    static volatile boolean running = false;
    private static volatile String lastArrival = null;
    private static volatile long lastArrivalAt = 0;

    private static class Pt { String id, name, loop; double lat, lng; }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> alerted = new HashSet<>();
    private List<Pt> points = new ArrayList<>();
    private double radius = 25;
    private boolean sound = true;
    private boolean countOpen = false;
    private long countEndsAt = 0;
    private String countPoint = "";
    private boolean listening = false;
    private long lastNotifUpdate = 0;
    private PowerManager.WakeLock countLock;

    private final Runnable countDone = () -> {
        if (!MainActivity.visible) alert("Count done", "Time is up" + (countPoint.isEmpty() ? "" : " at " + countPoint) + ".", null, false);
        countEndsAt = 0;
        releaseCountLock();
    };

    /** The point the service last alerted for while the app was off screen, once. */
    static String takeLastArrival() {
        String id = lastArrival;
        lastArrival = null;
        return (id != null && System.currentTimeMillis() - lastArrivalAt < 30 * 60_000L) ? id : null;
    }

    /** Whether the page's last update asks for the service. */
    static boolean wanted(Context c) {
        try {
            JSONObject s = new JSONObject(MainActivity.prefs(c).getString(MainActivity.PREF_STATE, "{}"));
            if (!s.optBoolean("on", false)) return false;
            JSONArray pts = s.optJSONArray("points");
            return (pts != null && pts.length() > 0) || s.optLong("countEndsAt", 0) > System.currentTimeMillis();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        makeChannels();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        // Must be called promptly after startForegroundService, whatever happens next.
        Notification n = watchNotification("Watching for your next point");
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID_WATCH, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        else startForeground(ID_WATCH, n);
        running = true;

        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!wanted(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        loadState();
        startListening();
        return START_NOT_STICKY;
    }

    private void loadState() {
        try {
            JSONObject s = new JSONObject(MainActivity.prefs(this).getString(MainActivity.PREF_STATE, "{}"));
            radius = s.optDouble("radius", 25);
            if (!(radius >= 5)) radius = 25;
            sound = s.optBoolean("sound", true);
            countOpen = s.optBoolean("countOpen", false);
            countPoint = s.optString("countPoint", "");
            List<Pt> list = new ArrayList<>();
            JSONArray pts = s.optJSONArray("points");
            if (pts != null) for (int i = 0; i < pts.length(); i++) {
                JSONObject o = pts.getJSONObject(i);
                Pt p = new Pt();
                p.id = o.getString("id");
                p.name = o.optString("name", p.id);
                p.loop = o.optString("loop", "");
                p.lat = o.getDouble("lat");
                p.lng = o.getDouble("lng");
                list.add(p);
            }
            points = list;
            long ends = s.optLong("countEndsAt", 0);
            if (ends != countEndsAt) {
                handler.removeCallbacks(countDone);
                countEndsAt = ends;
                long wait = ends - System.currentTimeMillis();
                if (wait > 0) {
                    holdCountLock(wait + 60_000L);
                    handler.postDelayed(countDone, wait);
                } else {
                    releaseCountLock();
                }
            }
        } catch (Exception e) {
            points = new ArrayList<>();
        }
    }

    /* ------------------------------------------------------------------ GPS */

    private void startListening() {
        if (listening) return;
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null) return;
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 0, this, Looper.getMainLooper());
            listening = true;
        } catch (SecurityException | IllegalArgumentException e) {
            stopSelf();
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location loc) {
        if (loc.hasAccuracy() && loc.getAccuracy() > MAX_ACC_M) return;
        Pt hit = null, nearest = null;
        double hitD = Double.MAX_VALUE, nearD = Double.MAX_VALUE;
        for (Pt p : points) {
            double d = dist(loc.getLatitude(), loc.getLongitude(), p.lat, p.lng);
            if (d > radius * 2) alerted.remove(p.id);            // walked away: may alert again
            if (d <= radius && !alerted.contains(p.id) && d < hitD) { hit = p; hitD = d; }
            if (d < nearD) { nearest = p; nearD = d; }
        }
        if (hit != null && !countOpen) {
            alerted.add(hit.id);                                  // the page alerts if it's on screen
            if (!MainActivity.visible) {
                lastArrival = hit.id;
                lastArrivalAt = System.currentTimeMillis();
                alert("You're at " + hit.name, "Tap to open the count.", hit.id, sound);
            }
        }
        long now = System.currentTimeMillis();
        if (now - lastNotifUpdate > 10_000L) {
            lastNotifUpdate = now;
            String text = countOpen ? "Count running" + (countPoint.isEmpty() ? "" : " at " + countPoint)
                    : nearest == null ? "Nothing left to count today"
                    : "Nearest to count: " + nearest.name + ", " + fmt(nearD);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.notify(ID_WATCH, watchNotification(text));
        }
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Override public void onProviderEnabled(@NonNull String provider) { }
    @Override public void onProviderDisabled(@NonNull String provider) { }

    private static double dist(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371008.8, p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        double dp = p2 - p1, dl = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * r * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    private static String fmt(double m) {
        return m < 1000 ? Math.round(m) + " m" : String.format(Locale.US, "%.1f km", m / 1000);
    }

    /* -------------------------------------------------------- notifications */

    private void makeChannels() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;

        NotificationChannel watch = new NotificationChannel(CH_WATCH, "Watching for points", NotificationManager.IMPORTANCE_LOW);
        watch.setDescription("Shown while GPS is on for arrival alerts.");
        watch.setShowBadge(false);
        nm.createNotificationChannel(watch);

        AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        NotificationChannel loud = new NotificationChannel(CH_ALERT, "Arrival and count alerts", NotificationManager.IMPORTANCE_HIGH);
        loud.setDescription("You've reached a point. Sound and vibration.");
        loud.enableVibration(true);
        loud.setVibrationPattern(BUZZ);
        loud.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), aa);
        loud.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(loud);

        NotificationChannel quiet = new NotificationChannel(CH_ALERT_QUIET, "Arrival and count alerts (vibrate only)", NotificationManager.IMPORTANCE_HIGH);
        quiet.setDescription("Used when \"beep as well as vibrate\" is off in Setup.");
        quiet.enableVibration(true);
        quiet.setVibrationPattern(BUZZ);
        quiet.setSound(null, null);
        quiet.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(quiet);
    }

    private PendingIntent openApp() {
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification watchNotification(String text) {
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, AlertService.class).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CH_WATCH)
                .setSmallIcon(R.drawable.ic_stat_bird)
                .setContentTitle("Arrival alerts on")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openApp())
                .addAction(0, "Stop until I open the app", stop)
                .build();
    }

    /** withSound false: vibrate only (the end of a count never beeps). */
    private void alert(String title, String text, @Nullable String pointId, boolean withSound) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        Notification n = new NotificationCompat.Builder(this, withSound ? CH_ALERT : CH_ALERT_QUIET)
                .setSmallIcon(R.drawable.ic_stat_bird)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVibrate(BUZZ)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(openApp())
                .build();
        nm.notify(ID_ALERT, n);
    }

    /* ------------------------------------------------------------ the count */

    /** Keeps the CPU awake for a running count, so the end alert is on time with the screen off. */
    private void holdCountLock(long ms) {
        releaseCountLock();
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm == null) return;
        countLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "birdcounts:count");
        countLock.acquire(Math.min(ms, 90 * 60_000L));
    }

    private void releaseCountLock() {
        if (countLock != null && countLock.isHeld()) countLock.release();
        countLock = null;
    }

    /* ------------------------------------------------------------ lifecycle */

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopSelf();        // swiping the app away turns the alerts off
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacks(countDone);
        releaseCountLock();
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm != null && listening) lm.removeUpdates(this);
        listening = false;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

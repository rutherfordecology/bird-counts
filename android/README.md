# Bird Count Field — Android app

The field app (`../field/`) in an Android shell. It adds **alerts with the screen off**,
which a web page can't give.

`WebViewAssetLoader` serves the page from `https://appassets.androidplatform.net/app/`.
That is a real origin, so geolocation, localStorage, the Cache API and the service
worker all work, and the offline map tiles are stored and served as they are in Chrome.
Leaflet 1.9.4 is bundled in `vendor/leaflet/` (SHA-256 of leaflet.js:
`db49d009…5641a`, the published file). Requests to unpkg are answered from it, so the
map works on first open with no signal.

## Building

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`. The `copyPage` task copies
the current `field/` files (page, species, service worker, sites) and Leaflet into
assets at build time. `versionName` and `versionCode` come from `VERSION` in
`../field/index.html` (`0.7.0` gives 700). `local.properties` holds the SDK path.

## Installing

Copy the APK to the phone, tap it in Files, and allow installs from that source once.
Allow location and notifications when asked.

The app keeps its own storage, apart from Chrome. Counts in the web app don't move
across by themselves: use GitHub backup or Data → Back up now in Chrome, then Restore in
the app. Rebuilds from this machine use the same debug key, so a new APK installs over
the old one and keeps the counts. Uninstalling deletes them.

## Screen-off alerts (AlertService)

- The page sends `BirdHost.update(json)` whenever something changes. The JSON holds the
  points not yet counted today, the radius, the sound setting, and whether a count is
  open and when it ends.
- The service is a foreground service of type `location`. It is only started while the
  app is on screen (Android 14 requires this), so it needs no background-location
  permission. It shows an ongoing notification with the nearest point still to count.
- Arrival uses the page's rule: within the radius, alert once; alert again only after
  walking more than twice the radius away. Fixes worse than ±75 m are ignored. While
  the app is on screen the service stays quiet, because the page alerts itself.
- At the end of a count it alerts if the screen is off. A partial wake lock is held
  while the count runs, so the alert is on time.
- It stops when nothing is left today and no count is running, when you tap Stop on the
  notification, or when you swipe the app away. Opening the app starts it again.
- Some phones (Samsung, Xiaomi and others) kill background apps hard. If alerts stop
  coming, set the app's battery use to Unrestricted.

## What else the shell adds

- **Exports:** a WebView ignores downloads, so files go through `BirdHost.saveFile`,
  which offers Save to Downloads or Share. Cancel tells the page it wasn't saved.
- **Import and restore:** the file picker.
- **Sites:** the page asks the live GitHub Pages site list first, and falls back to the
  copy built into the APK when there's no signal.
- **Back button:** closes a sheet or dialog, then returns to the map, then exits.
  During a count it does nothing but remind you to save or discard.

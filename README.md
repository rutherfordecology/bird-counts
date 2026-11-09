# Bird counts

Two small browser tools for five-minute bird counts. Both are plain HTML files with no build step and no server.

- **Planner** (`index.html`, for the desktop). Draw a route, and count points are placed along it at a set straight-line spacing (default 200 m). Existing stations and photopoints can be imported from xlsx, csv, KML/KMZ, GPX or GeoJSON. Sites can be saved, and points exported as GPX, KML, CSV, GeoJSON or a print sheet.
- **Field app** (`field/`, for the phone). Load the planner's GeoJSON. The app shows the next point on an offline map, alerts you on arrival, and runs the 5-minute count. Species are listed in order of how likely they are on that loop, with "Other" to search eBird's species list. You can export your own CSV, an eBird upload CSV, or a backup. Add it to the home screen to use it offline.

Data stays in the browser or on the phone it was entered on; nothing is sent anywhere.

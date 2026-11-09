# Changelog

## v0.6.0 — 2026-11-09
- "Add point": click on the map to add your own points, e.g. at wetlands. They can sit closer than the spacing; generated points further along the path re-space from them and everything is renumbered in order. Added points are teal and can be dragged; each belongs to the path it's nearest to.
- Delete any point from its popup or with × in the table. Deleting a generated point keeps the points after it where they are, measured from your added points if any are nearby; "Restore deleted" brings deleted points back.
- Points under the spacing from another point get an amber ring, and the summary counts them, so you can decide which to keep.
- Added points and deletions are saved with the site and in the GeoJSON file; GPX, KML, CSV and the print sheet mark added points. GPX and KML don't keep deletions.

## v0.5.0 — 2026-11-09
- Drag and drop files anywhere on the page to import them; several at once is fine.
- KMZ files can be imported, as well as KML, GPX, GeoJSON, xlsx and csv.
- One "Import file…" button replaces "Open file" and "Add existing points…". A site saved from this tool opens as that site; anything else is added to the current site: lines become paths, points become existing stations, or photopoints when their name, folder or style mentions "ppt" or "photo".
- Points already on the map aren't added twice, and points outside NZ are ignored. A status line reports what each file added.

## v0.4.0 — 2026-11-09
- "Add existing points…" loads existing stations and photopoints from a spreadsheet (.xlsx or .csv): line, number, and NZTM easting/northing or latitude/longitude, found by column header. Sheets named "ppt"/"photo…" are read as photopoints.
- Existing stations show as blue squares, photopoints as purple circles; photos taken from the same spot share one marker. Labels appear from zoom 15; both can be switched off in the layer menu.
- Existing points are saved with the site, in the browser and in the GeoJSON file.
- Fix: reopening a site with no generated points no longer wipes the existing points from the work-in-progress copy on the next reload.
- Fix: labels sit beside their markers instead of on top of them after zooming in.

## v0.3.0 — 2026-11-09
- Save sites by name in this browser and reopen them from a list; delete ones you no longer need. Status line shows which site you're on and whether it has unsaved changes.
- Save to file / Open file: a GeoJSON file holding the paths and points (also opens in QGIS). Open file also takes GPX and KML — tracks become paths, and points exported from this tool come back as points.
- Reopened sites show their points exactly as saved; points are only recalculated after you edit a path or change the spacing or prefix.
- "Clear" is now "New", which starts a fresh site.

## v0.2.0 — 2026-11-09
- Spacing is now straight-line ("as the crow flies"), not distance along the path. Each point goes at the first spot along the path that is at least the set distance from every point already placed, including points on other paths, so curves, switchbacks and nearby paths never put two points too close.
- Point table shows the straight-line gap to the previous point; popups also show the distance to the nearest other point.
- Removed the "point at path end" option, since an extra end point would usually sit closer than the set spacing.

## v0.1.0 — 2026-11-09
- First version: draw one or more paths on an aerial/topo map; count points are placed every N metres (default 200 m) measured along the path from its start.
- Optional point at the path end, editable name prefix (P01, P02…), NZTM2000 coordinates for each point.
- Export points (and the path) as GPX, KML or CSV; print sheet with map and a point table with blank columns for field notes.
- Paths and settings are remembered in this browser.

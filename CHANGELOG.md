# Changelog

## v0.8.4 — 2026-11-09
- KML import reads each point's attribute fields: line and station number (e.g. "Line_number__optional_" and "Station_number" in the Waimata KML). Stations imported from such a KML now have their line, so they get labels like 2-05 and a route per line. Before, they came in with no line.
- A line like "2p" marks a photopoint. Otherwise the nearest folder decides ("ppts…" or "stations…"), so a document named "ppts and stations" no longer turns every point into a photopoint.

## v0.8.3 — 2026-11-09
- Importing bird-count data (eBird-style sheets such as the 2024 Waimata data): the station now comes from "Location Name", not from "Number", which there is the count of birds. Before, such an import gave one point per bird count, numbered by how many birds were seen.
- One point per station: where rows give a station several positions, the one most rows agree on is kept, and the import message lists those stations.
- A station numbered 101–499 with no line is put on line 1–4 by its hundreds (205 → 2-05), including in sites already saved in the browser, so its line gets a route.

## v0.8.2 — 2026-11-09
- Existing stations on each line are joined by a blue line in number order (1-01 → 1-02 → …), so each line shows as a route to follow. Hover it to see which line it is.

## v0.8.1 — 2026-11-09
- Existing stations are labelled line-number with two digits on the map: 1-01, 1-02 … 2-01, 2-02, instead of 1-1, 1-2.

## v0.8.0 — 2026-11-09
- Sites on GitHub. Under the new GitHub section, add an access key once; after that, Save also puts the site in the repo (`field/sites/<name>.geojson`, listed in `field/sites/index.json`). The field app then offers it under Built-in sites about a minute later. Every save is kept in the repo history.
- "Open a saved site…" also lists the sites on GitHub (no key needed to open them), so a site can be opened and edited from any computer. Opening one keeps a copy in this browser.
- The save line says whether the site reached GitHub ("✓ on GitHub", or why not, and to press Save to retry). Saving in the browser never depends on GitHub.
- The version number in the header was stuck at v0.6.0; it now shows the real version.

## v0.7.0 — 2026-11-09
- Name prefix per path: with two or more paths, each gets its own prefix box under "Name prefix" (blank = the main prefix), showing its point count and name range. Numbering starts at 01 for each prefix and runs on across paths that share one. Hovering a path on the map highlights its box.
- Drag any point to move it, e.g. onto a track. A moved point stays where you put it (marked "moved"), its old spot isn't refilled, and points further along re-space from it. Its popup has "Put back". "Restore deleted" leaves moved points alone.
- Path prefixes and moved points are kept in saved sites and the GeoJSON file; GPX, KML, CSV and the print sheet mark moved points.

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

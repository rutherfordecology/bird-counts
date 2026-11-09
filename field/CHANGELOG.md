# Bird Count Field — changelog

## v0.1.0 — 2026-11-09
- First version of the phone app for five-minute bird counts, a companion to the Bird Count Points planner.
- Load points from the planner's GeoJSON. Existing stations become loops by line (Line 1–4) and are named as in past data (101, 102…); drawn paths become Path 1, Path 2…; photopoints are left out.
- Map with your position, the next point (distance, direction, arrow) and a dashed line to it. Points turn green once counted today.
- Arrival alert: vibrate, beep and a banner when you're within 25 m (adjustable) of a point not yet counted today. It works while the screen is on and the app open; the app keeps the screen awake.
- Count screen: start the 5-minute timer (or just tap the first bird). Species are listed in a fixed order of how often each was recorded on that loop in the 2023–24 Waimata counts, with the percentage shown. Tap to add one, − to take one off. At 5 minutes it buzzes and locks the counts (Edit to correct them).
- "Other…" searches eBird's full species list (English NZ names, common or scientific), or keeps a name exactly as typed. Added species stay on the list for next time.
- Data tab: counts by day, delete a count, and three exports: your own CSV (survey, loop, point, date, start time, duration, observer, point and GPS coordinates, species, count; one row per species, plus a row for counts with nothing seen); an eBird upload CSV (English NZ names, MM/DD/YYYY, no BOM, loop in the checklist comments); and a JSON backup that can be restored.
- Offline: the app caches itself, and Setup downloads aerial (optionally topo) map tiles around the points.

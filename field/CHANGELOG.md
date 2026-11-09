# Bird Count Field — changelog

## v0.4.0 — 2026-11-09
- Backup to GitHub (Setup): with a repo name and an access key, every saved count is copied to GitHub whenever there's reception. It tries right after each save, when signal comes back, when you return to the app, and every minute while something is waiting.
- Files in the repo: `counts/<date>-<phone>.csv` (same columns as "My data"), `counts/<date>-<phone>.json`, and `backups/<phone>-latest.json` (restorable). Deleting a count updates that day's files; the old version stays in the repo history. Unchanged files aren't re-committed.
- The Data tab shows the upload state ("✓ Every saved count is on GitHub" or "1 day waiting to upload"). Counts on GitHub count as backed up, so the "only on this phone" warning clears once they're uploaded.
- The access key is stored apart from your data, so it never ends up in a backup or export. "Forget key" removes it.

## v0.3.0 — 2026-11-09
- A count in progress is saved on the phone after every tap. If the phone dies, the app crashes, or Android closes it while you're in another app (e.g. Merlin), it reopens on that count exactly as you left it: birds, added species and timer. If the 5 minutes ran out meanwhile, it opens as finished.
- If the phone ever refuses to save (e.g. storage full), you get a loud warning and the count stays open instead of disappearing.
- Data tab warns when counts have never left the phone ("⚠ 3 counts only on this phone") with a "Back up now" button. Cancelling the share sheet doesn't count as backed up. Finishing a loop offers a backup straight away.
- The app asks the phone to protect its storage, and Setup shows whether it agreed. A new Setup card explains how data is kept safe.
- Discarding a count asks first and names the point.

## v0.2.0 — 2026-11-09
- Built-in sites: the Waimata stations (124 on Lines 1–4) are part of the app, so it opens ready to use with nothing to load. Setup → Built-in sites switches between sites; loading your own points file still works.
- Photopoints show on the map as small purple dots for reference (not counted).
- Counting screen: likelihood reads "41% of past counts on Line 1", or "not yet on Line 1 · 6% overall"; the species list opens at the top for each new count.
- App updates arrive on the next open when there's signal (it waits up to 3 s, then uses the saved copy).
- Map buttons rearranged so nothing covers the arrival banner or the next-point card.

## v0.1.0 — 2026-11-09
- First version of the phone app for five-minute bird counts, a companion to the Bird Count Points planner.
- Load points from the planner's GeoJSON. Existing stations become loops by line (Line 1–4) and are named as in past data (101, 102…); drawn paths become Path 1, Path 2…; photopoints are left out.
- Map with your position, the next point (distance, direction, arrow) and a dashed line to it. Points turn green once counted today.
- Arrival alert: vibrate, beep and a banner when you're within 25 m (adjustable) of a point not yet counted today. It works while the screen is on and the app open; the app keeps the screen awake.
- Count screen: start the 5-minute timer (or just tap the first bird). Species are listed in a fixed order of how often each was recorded on that loop in the 2023–24 Waimata counts, with the percentage shown. Tap to add one, − to take one off. At 5 minutes it buzzes and locks the counts (Edit to correct them).
- "Other…" searches eBird's full species list (English NZ names, common or scientific), or keeps a name exactly as typed. Added species stay on the list for next time.
- Data tab: counts by day, delete a count, and three exports: your own CSV (survey, loop, point, date, start time, duration, observer, point and GPS coordinates, species, count; one row per species, plus a row for counts with nothing seen); an eBird upload CSV (English NZ names, MM/DD/YYYY, no BOM, loop in the checklist comments); and a JSON backup that can be restored.
- Offline: the app caches itself, and Setup downloads aerial (optionally topo) map tiles around the points.

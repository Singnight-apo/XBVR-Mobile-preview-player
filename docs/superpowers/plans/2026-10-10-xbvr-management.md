# XBVR native management implementation

Approved scope: Android App only; existing XBVR HTTP APIs plus read-only WAMP task lock events. Branch feat/xbvr-management from main eda1b4b; retain branch, no merge/push/release/version bump.

1. Management API + MockWebServer tests: native server directories, sites, tasks, files/search/match; classify unavailable/auth/network, no mutation retries, readback verification.
2. Settings entry + native maintenance/scraper/matching/task screens, current server identity, bilingual strings. Preserve filters/routes/scroll, bind asynchronous callbacks to server and lifecycle.
3. Foreground state poll every 3 seconds on task page, WAMP lock.change monitoring. Initial unlocked means idle; observed running then unlocked means ended, not proof of success. Disconnect means unknown. No fake history/progress.
4. Test synthetic fixture and phone flow, rotation/back/server switch/offline and regression. Do not mutate production media library. Report separately what real XBVR engine behavior remains untested.

Implementation evidence and QA outputs go to D:/codex-work/output/xbvr-management-20261010. Commits grouped API, maintenance/scraping, matching, verification. No entity deletion or advanced server configuration.

# Server removal implementation plan

Approved design: each server row has a Remove button and address-specific confirmation. Removing an inactive server preserves the current page. Removing the active server selects the first remaining server; removing the last server returns to the connection page. Only local connection configuration is removed; viewing records and server files are retained.

Implementation follows existing Java UI/domain/data boundaries without new dependencies.

- [x] Add regression tests for inactive/active/last removal, stale callbacks, and persistence failure.
- [x] Implement removal coordination in LibraryController and MainActivity.
- [x] Add accessible row buttons and confirmation, with Chinese and English resources.
- [x] Run JVM tests/lint, build and verify the signed candidate without changing release version.
- [x] Validate cancel and actual removal of the temporary server on the connected phone; verify existing servers remain.

Validation: 192 JVM tests, no failures/errors; lint 0 errors and 17 warnings; signed release candidate verified with the existing certificate. On Xiaomi 17 Pro Max / Android 17, cancel preserved all profiles; inactive removal preserved the active HTTPS server; active temporary-profile removal selected the first remaining LAN server. Original HTTPS selection was then restored. Last-profile removal is covered by JVM tests; real user profiles were not removed to exercise that case.

No GitHub publication is part of this change. Candidate artifact is separately named; existing published files are retained.

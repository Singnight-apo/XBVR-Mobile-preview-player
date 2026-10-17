# Four approved management layouts implementation plan

> **For agentic workers:** Use executing-plans for the existing feature branch. Codex implements native UI, DSH runs bounded tablet QA; no concurrent source edits. Steps use checkboxes.

**Goal:** Implement the four user-approved XBVR Pocket management layouts, then validate on the connected tablet first.

**Architecture:** Keep current API, lifecycle cleanup, confirmation, capability and write-verification code. Render directory data through StorageDirectoryViews and choose the presentation from actual content width, device class, orientation and font scale. Keep Ui system/cutout insets and account for them when choosing density.

**Tech Stack:** Existing Java Android native views, bilingual XML resources, JUnit, Gradle, ADB.

**Visual sources:** D:/codex-work/outputs/xbvr-layout-unification-20261017/references. Phone portrait = selected option 2; phone landscape = final grouped-list revision; tablet landscape = selected option 1; tablet portrait = selected option 2. Grouped continuous surfaces, mint accent, common margins, clear label/value alignment. Raster mock text/data/icons are visual references; real data and existing native icons remain authoritative.

## Task 1: Preserve baseline and define density policy
- [x] Save current modified/untracked source snapshot and HEAD before editing; preserve earlier directory management and HTTPS fixes.
- [x] Add tests to ManagementLayoutTest: tablet landscape TABLE, tablet portrait GROUPS, phone landscape ROWS (never TABLE), phone portrait CARDS, reduced safe content width / enlarged font falls back to stacked CARDS.
- [x] Run focused test and observe failures before adding the contextual storage-mode overload. Preserve existing shell PHONE/TABS/SIDEBAR rules and existing navigation/back tests.

## Task 2: Native directory rendering
Files: StorageDirectoryViews.java, ManagementView.java, storage_strings.xml in values and values-zh.
- [x] Replace individually bordered directory cards inside an outer card with one continuous list surface and separators.
- [x] CARDS: path/status/more at top, three equal metric columns, dedicated footer with last-scan and scan button. Narrow/large-font footer stacks if needed.
- [x] GROUPS (tablet portrait): same three-layer list structure, consistent wide columns; no table header.
- [x] ROWS (phone landscape): left path/status, center three repeated label/value columns, right scan/more; no table header. Fallback to CARDS if content cannot fit.
- [x] TABLE (tablet landscape): shared seven-column header, stable numeric/action widths, paths wrap, all rows share column alignment.
- [x] Move heading/summary/actions outside the directory surface. Show large capacity summary for portrait/tablet, compact capacity+totals for phone landscape. Primary add, batch scan, and matching controls share heights; matching appears once with live unmatched count.
- [x] Preserve capability gates, identity, unknown-data values, scan tick updates, cancellations and all callbacks. No API or server mutation semantics changes.

## Task 3: Safe shell and verification
- [x] Use measured safe content width after layout; rerender only on actual width change so system/cutout insets and split windows cannot leave stale density. Keep Ui.insets and original system settings.
- [x] Build assembleDebug, full testDebugUnitTest, lintDebug via existing build.ps1. Require zero test failures and zero lint Error/Fatal; record warnings.
- [x] Review change independently, resolve substantive findings, verify git diff --check, preserve no-commit/no-push/no-release boundary.

## Task 4: DSH tablet-first acceptance
- [x] DSH standard/danger-full-access: identify actual tablet, record initial locale/night/rotation/foreground and device time; never substitute a phone if tablet absent.
- [x] Install exact delivered APK with -r, compare host/device SHA256. Capture Chinese/English landscape/portrait PNG+XML; actual image dimensions and package ownership decide validity.
- [x] Validate all directories reachable, read-only stats match live API, detail and add error/cancel, remove and scan confirmation cancel, navigation/rotation, light portrait/landscape. Check dynamic font/width fallback if practical without changing user settings permanently.
- [x] Save source mock and same-state device screenshots together for design comparison; fix real clipping/alignment defects and revalidate affected modes.
- [x] Inspect system exit-info/dropbox by post-install timestamps (not bounded-history count deltas); use logcat only if app evidence is sufficient. Never clear logs.
- [x] Read-only real server: no scan, add/remove, matching or scrape writes. Compare directory IDs/path before/after. Restore actual initial device settings and retain APK/data.
- [x] Codex independently checks screenshots/XML, package hash, logs and restored state. Report tested versus untested boundaries, including phone real-device testing deferred until after tablet.

User has approved design and execution; no additional design-approval gate. Work continues on the existing feature branch with baseline backup; DSH edits QA artifacts only. No commits, merge, push or publication in this batch.

## Task 5: Xiaomi13 after tablet acceptance (user follow-up authorization)
- [x] Only after tablet QA and independent readback complete, identify the wireless fuxi target and independently capture its own initial locale/night/rotation/font/foreground/lock state.
- [x] Test normal wake, current unlocked state, app launch and ADB input; preserve exact errors if system restrictions remain. Do not remove credentials or change security/lock settings.
- [x] Install the same verified APK, check device hash, and capture Chinese/English portrait/landscape with real PNG dimensions and package XML. Compare phone portrait to selected option2 and landscape to final grouped-list mock.
- [x] Validate safe cutout/gesture edges, navigation menu, three metrics and action visibility, HTTPS language rebuild/entry/exit, add error/cancel and scan/remove cancel where device input is available.
- [x] Compare server ID/path read-only, inspect exit-info/dropbox timestamps, restore actual phone baseline and retain app/data. Codex independently checks the evidence.

## Budget continuation (user sleeping)
- [x] Inspect actual account five-hour allowance at meaningful milestones. If nearly exhausted before completion, save outputs/xbvr-layout-unification-20261017/CONTINUE.md and schedule a heartbeat on this same thread after the actual reset time. Do not create a separate task or reset credits. Keep unchanged/nonactionable follow-ups quiet; report completion or concrete required action.


Final acceptance: c7fd3578…995f4, latest user type-scale override applied. Both devices independently verified, baseline restored. Tablet deep-flow version limits and overwritten old light/font evidence are disclosed in design-qa.md and outputs acceptance.md. Allowance naturally refreshed; no automation needed. No commit/push/release.

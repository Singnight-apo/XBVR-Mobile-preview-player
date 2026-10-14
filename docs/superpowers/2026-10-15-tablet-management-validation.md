# Responsive management layout validation

Date: 2026-10-15. Candidate based on v0.2.10 (versionCode 13); no version bump or release.

## Build and static checks

- `build.ps1 -Tasks @('--offline','testDebugUnitTest','lintDebug','assembleRelease')` completed successfully. The final pre-commit check reused unchanged Gradle outputs.
- JVM report: 251 tests, zero failures/errors/skips, including six new layout/navigation policy tests.
- Lint: zero errors, 13 warnings in files outside this change.
- English and Chinese string-key sets match: 397 keys each.
- Independent read-only review found no blocking issues in layout policy, configuration restoration, navigation or operation guards.

## Device checks

| Device | Platform | Coverage |
| --- | --- | --- |
| Xiaomi Pad 5 Pro (M2105K81AC) | Android 13 | Chinese/English portrait and landscape maintenance layouts, four category routes, rotation retaining maintenance, sidebar return to Settings, scan confirmation cancelled |
| Xiaomi 13 (2211133C) via wireless ADB | Android 16 | Data-preserving upgrade from 0.2.9 to candidate, original phone management home, four category routes and back paths, Chinese/English portrait and landscape maintenance, landscape content scrolling, scan confirmation cancelled |

Candidate SHA256: `64d38a66bfbbb4b1bd7c572bae2e92ff1e305b63fa358e1a35669c241f167b71`. Installed APK hashes on both devices matched. Signing certificate SHA256: `20c3404b32ff065f1e18159e36058ac16161f8531b07dd5b7ce4a6f0627bb8f4`.

Language and rotation settings were restored. The Xiaomi 13 was relaunched after restoring its application locale; its final process crash buffer was empty. This is current-process evidence, not exhaustive crash-free or playback acceptance.

Private UI screenshots and XML remain in local output directories and are excluded from Git. The initial Xiaomi 13 English capture sequence showed system Settings in the foreground and was discarded as app evidence; replacement `actual-*` captures verified the app itself.

## Limits

No server scan, scrape or scene-refresh operation was submitted. No playback/VR, large-font or real split-window regression was performed. Narrow-window behavior is covered by policy unit tests. No remote push or GitHub Release is part of this validation. The pre-existing local `build.ps1` toolchain modification is excluded from the UI commit.

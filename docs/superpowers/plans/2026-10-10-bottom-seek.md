# Bottom-region seek gesture

Approved design: lower 20% of usable video view accepts predominantly horizontal single-finger seeking, 120 seconds per viewport width; all other single-finger drags pan. Lock routing from touch-down after touch slop. Preview time/progress during the gesture; commit once on release. Cancel on multi-touch, system interruption, resizing or leaving the player. Preserve taps, double taps, pinch, playback intent and native control priority. Unknown/unseekable duration disables seeking. Exclude mandatory navigation inset.

Implementation: pure Domain SeekGesture state machine and JVM routing/clamp/cancel tests; VrView routes touch callbacks; PlayerActivity connects current Media3 seekability/position/duration and commit; PlayerView owns preview HUD and ticker/hide suppression. Localized hints. No renderer/protocol changes.

Validation: red/green JVM tests, full tests/lint/release build, synthetic video test on Xiaomi 13 for horizontal seek, upper/vertical pan, cancellation, original SeekBar, playback intent, landscape and preview-return. Preserve profiles/version/branch; no release.

# Four approved native management layouts — design QA

Date: 2026-10-17. Sources: D:/codex-work/outputs/xbvr-layout-unification-20261017/references. Native Android production implementation, not a web prototype. Device OS bars, configured server address, live data and existing native icons remain authoritative.

## Findings, iteration 1
- [P2] Tablet portrait scan action was 96 physical pixels rather than 96dp. In initial-portrait-zh.png the two Chinese characters wrapped vertically. Fix: convert the fixed width through dp(96); retain 48dp minimum button height. Re-capture required.
- Tablet landscape initial comparison: sidebar/full seven-column table, aligned stats and continuous surface present; portrait top tabs/three-stat groups present. Existing native fonts/colors intentionally retained. Actual file count1291 differs from the concept1290 because live server data is used; source sample data is not hardcoded.
- Scan play icons and top-tab icons added using the existing native icon set. No new raster artwork or custom icon drawing introduced.

## Tablet verification, final package 33c48351…82759

Codex inspected each tablet reference together with current `cap-landscape-zh.png` and `cap-portrait-zh.png`. Landscape sidebar/seven-column table, portrait four top tabs/continuous three-layer groups, capacity, summary actions, mint accents, path hierarchy, labels/values, separators and scan actions now follow the selected visual direction. Native full-width app bar, system bars, solid surfaces, configured address, real data and native/official Material icons are explicit adaptations; this is not a pixel-identical raster clone.

- [P2, fixed] Portrait scan button dp sizing: current captures show horizontal Chinese labels.
- [P2, fixed] English portrait capacity/caption clipping: measured caption now moves below the large number if it cannot fit beside it. `cap-portrait-en.png` shows the complete value and single-line caption below.
- [Test defect, closed] DSH initially tapped nonclickable `android:id/alertTitle` instead of submitting. Corrected clickable Button `android:id/button1` tests show errors for empty/relative paths, intact cancellation and zero overlap. `form-final-empty-error` and `form-final-relative-error` PNG/XML independently inspected; no source fix needed.

Current-package evidence includes bilingual overview matrix, light portrait/landscape and corrected invalid-path tests. `codex-artifact-readback.json` checks actual dimensions, package ownership, three visible paths/live totals and error/cancel geometry. `codex-tablet-readback.json` confirms installed hash, restored baseline and no crash/ANR in the current installation window. Current authoritative install record is `install-cap.json`; older `install-final.json` must not override its version/time.

Deeper details/confirmation-cancel/matching navigation/rotation/1.3 font checks belong to historical `324d…`; final source change only fits the overview caption. These are not represented as a full repeat on the final package. Server IDs/paths/counts are unchanged and no real add/remove/scan/matching writes executed. Full accessibility and successful server writes are untested.

Final build: 290 tests, zero failures/errors/skips; Lint29 warnings, no Error/Fatal. Eight prior data/domain/HTTP lifecycle/toolchain files remain identical to saved baseline. No commit/push/release.

## Remaining
Latest user instruction supersedes the mockups' large type: align the management page with existing settings/library screens. Current package `c7fd3578…995f4` uses 20sp section headings, 32sp capacity, 15sp paths, 16sp metrics, 14sp actions/navigation and 12sp supporting text; 48dp touch targets are preserved. Phone portrait matching uses a shorter counted label with full contentDescription; compact landscape includes live recent-scan text. Codex inspected `ph3-portrait-zh-first` and `ph3-landscape-zh-first`: the count no longer wraps alone and scan ages are present. Independent code review found no substantive P1/P2.

Current-package acceptance completed: tablet `tb3-*` and phone `ph3-*` bilingual portrait/landscape matrix, Chinese light variants, tablet font_scale=1.3 portrait English, and phone cancel-only flows. Codex inspected all eight current overview screenshots, four light screenshots, tablet enlarged-font capture, and phone detail/error/remove/scan dialogs. Independent `verify_final_matrix.py` verifies actual PNG dimensions, package ownership, headings, live counts/capacity and paths. Phone scrolling reaches all directories; header menu routes to tasks and back. Real device Insets establish a 121px landscape left cutout and 44px bottom navigation area; the earlier hardcoded 60px check incorrectly flagged the app background and a clipped scroll child, rather than an overlapping action.

Codex independently read both final installed APK hashes and original settings: both run `c7fd3578c97cebd270cdd198aba4f719cd41515d457530f941fb69f5eab995f4`; tablet returned to MainActivity, phone returned through the normal recents UI to original settings task109/SubSettings and original Asleep/OFF state. Current installation windows contain no exit reasons4/5/6. Phone logcat application-PID coverage is insufficient; crash conclusions rely on system exit records/Dropbox rather than empty logcat. Independent read-only GET verifies directory IDs/paths/counts/size/last-scan unchanged.

Evidence provenance correction: reusing the tablet light/font script replaced four historical 324d files (`light-landscape-zh`, `light-portrait-zh`, `font13-portrait-en`, `lighttheme-font.json`) with current c7fd evidence. Explicit `tb3-*` same-source copies and provenance JSON identify the new version. Historical 33c light2/cap and 324d fmx/dp artifacts remain. Tablet business deep-flow evidence retains its older package boundary; phone deep checks were run on c7fd. Full accessibility and actual server-write outcomes remain outside scope.

## User-found visual defect after acceptance

User correctly identified portrait scan outlines touching the metric divider on both devices. Previous visual acceptance missed this defect. Saved c7fd XML independently confirms 0px separation: phone full scan bounds start at y1363/1871, exactly the divider bottoms; tablet starts y1072/1454/1836, likewise exactly the divider bottoms. The outline filled its touch bounds, while the group added8dp only below the footer, leaving unequal spacing to the upper/lower separators.

User supplied an enlarged screenshot and requested a smaller symmetric outline. Final fix85f84010…1b197 uses an8dp uniform corner radius and an InsetDrawable (left/right4dp, top/bottom6dp) for directory scan buttons, preserving48dp touch bounds and centered content. The extra group bottom8dp is removed, so portrait outlines sit equally between separators. No footer padding was added. Await tablet-first and phone portrait dark/light + landscape checks. `verify_scan_outline.py` measures colored outline pixels separately from touch bounds and checks symmetry plus equal upper/lower divider gaps. Previous passing acceptance remains withdrawn pending this targeted verification.

Targeted verification completed on85f: Codex independently inspected corrected tablet and phone dark/light captures and verified nine image/XML pairs (`codex-outline-matrix.json`). Tablet outline insets are9/14/9/14px, upper/lower divider gaps14/14px; phone insets11/17/11/17px, divider gaps17/17px. Every full scan target remains48dp high. Two devices independently report85f hash and restored locale/font/theme/rotation; phone is asleep. Fresh phone pre-test component MorningGreetActivity t125 was not restored (XBVR remains the resumed task while asleep); obsolete night-time task109 was not forced forward. Server ID/path/count/size/last_scan remain unchanged; new installation windows have no crash/ANR reasons4/5/6. Final repair assembled/linted successfully;290 unit tests belong to the previous type-scale build and were not repeated for this drawable/padding-only repair. The intermediate95e tablet capture is retained separately; only85f is final acceptance.

The preceding awaiting-verification paragraph records the earlier gate and is now resolved by these device measurements.

final result: passed (symmetric smaller scan outlines; explicit coverage/restoration limits above)

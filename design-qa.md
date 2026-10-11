# Media-library maintenance UI — 2026-10-11

Selected target: second displayed revised mock, `exec-f3143cc1-fc80-4f92-be46-1f85e9a890cc.png`.
Evidence: `D:/codex-work/output/xbvr-maintenance-layout-20261011/`.

## Comparison

- Inspected `comparison.png`: reference and final Xiaomi 13 screenshot together, normalized to 390 pixels wide without stretching. Reference is 841×1870; device screenshot is 1080×2400, with OS bars excluded for comparison. Native OS bars remain system-owned.
- Typography: native app font retained, readable path/time hierarchy; final `21-final-large.png` confirms 1.3 font scale no longer strands the final character of either action title. Directory paths may wrap up to three lines.
- Spacing: directory heading and scan-all action share a row; each directory has an inline scan action. Two lower actions occupy equal columns with a 10dp gap. Horizontal layout scrolls vertically to reach the lower actions (`17-landscape-bottom.png`).
- Colors: existing light-theme background, accent, muted text and soft button fill retained. Dark theme uses the existing theme tokens; it was not separately device-tested in this task.
- Assets: no raster artwork required. Existing native library/refresh icons intentionally replace the mock's folder/list icons; no new icon drawing or image approximation was introduced.
- Content: actual configured server address replaces illustrative server identity. Server timestamp appears under each path. All three displayed relative times agree with server values; tapping shows the complete local timestamp.
- Interactions: directory refresh, timestamp details, scan confirmation/cancel, scene-refresh confirmation/cancel, App-list reload, and landscape navigation checked. Colored lower tiles are single buttons, so their subtitle regions also activate the action. Write operations retain capability checks and duplicate-request guards.

## Scope and remaining differences

Native header/server identity, existing typeface, rounded button styling and icons are intentional adaptations to the current App. Exact mock pixel spacing is not claimed. No server scan or scene-refresh task was executed against the real library. Automated coverage checks timestamp parsing, missing/Go-zero values, timezone equivalence and minute/future-clock boundaries. Actual server scan-end notification handling was implemented but not exercised by starting a production scan.

Final result: passed for the selected native layout and checked interactions.

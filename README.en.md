# XBVR-Mobile preview player

[中文](README.md) | **English**

An independent Android client for browsing an XBVR library and playing standard and VR videos on a phone or tablet. Enter an accessible XBVR server address, browse the poster grid, and open a video in the app. VR is displayed through a single-eye viewport on the device screen, with touch navigation, pinch zoom, and optional orientation-sensor controls. The app uses native Android UI, Media3 decoding, and OpenGL ES rendering.

**Version 0.2.7 · Android 10 or later.** The APK follows the system language: Chinese on Chinese-language systems, English on other systems. Android 13 and later also allow choosing the app language in system settings. The library and settings follow the system light or dark theme. Playback controls retain a dark overlay for visibility over video. The poster wall uses wider cards and tighter gutters so more covers fit on screen; when a fixed cover ratio is selected, each cover is centre-cropped to fill its card.

[Download the latest APK](https://github.com/Singnight-apo/XBVR-Mobile-preview-player/releases/latest). See [VALIDATION.md](VALIDATION.md) for the recorded test environment, results, and limitations; results from earlier releases do not automatically validate this version.

The screenshots below show the app on a real device; library details have been obscured. The app includes no movie library; actual content comes from the XBVR server you connect.

<p>
  <img src="docs/screenshots/library.jpg" alt="Library" width="240">
  <img src="docs/screenshots/player.jpg" alt="Player" width="240">
</p>

## Installation and connection

1. Download the **0.2.7** APK from the release page, transfer it to your phone or tablet if needed, and open it. Follow Android's prompt to allow installation from the file manager or browser you are using.
2. To update an existing installation, install an APK signed with the same key over the current app. The distributed update retains the development signing identity used for previous deliveries, preserving saved servers, local favorites, and resume records. Uninstalling first removes local app data.
3. Open the app and enter an XBVR address the device can reach, such as `http://YOUR-XBVR-HOST:9999`. For a LAN server, the device needs a working network route to that server.
4. Enable XBVR's **DeoVR interface** and player access for the playlists you want to browse. The poster grid uses `/deovr`; enabling only HereSphere does not make the library catalog available.
5. If XBVR requires a player account, enter its player username and password. If your reverse proxy separately requires HTTP Basic authentication, use the independent proxy authentication fields. Leave unused authentication fields blank.
6. Select **Save and connect**. Multiple server profiles can be saved, selected, and edited through the server menu in the library. Expand the proxy authentication settings to edit the separate HTTP Basic credentials.

Addresses may include a reverse-proxy path prefix or end in `/deovr` or `/heresphere`; the app normalizes them to the server's root prefix. HTTPS uses normal certificate validation. If a player JSON request redirects to a different server origin, the app stops forwarding player credentials; enter the final XBVR address instead.

HereSphere details are an optional supplement for richer file lists, chapters, subtitles, and favorite permissions. If unavailable, details fall back to DeoVR. The app preserves server-provided media URLs and query parameters. For videos with multiple files, it tries to obtain reliable metadata for the file actually selected; confirm the format manually when that information is unavailable.

## Library

- The server menu uses spaced neutral action rows; only the selected server keeps its green highlight. Rows have 8dp gaps and a minimum 48dp touch height.



- **Complete posters:** covers fit inside their frames without center cropping. Automatic aspect ratio uses the first valid cover's original ratio. The server menu offers Auto, 1:1, 3:2, and 16:9, saved per server. Differently shaped covers may have empty margins. Phones show at least two columns; tablets and wider windows add columns. Cards show the title, duration, favorite marker, and resume progress.
- **Metadata search:** the search field matches titles, studios, actors, and tags. Category selection preserves each XBVR playlist's original ordering, including custom playlists ordered by added date.
- **One filter row:** category selection, studio/actor/tag controls, selected conditions, and clear-all share one horizontally scrollable row. The movie count appears at the end. Landscape mode does not add another filter row.
- **Combined filters:** tap a studio or actor on a card, or use the filter selectors. Studio and actor each select one value. Tags allow multiple selections and match any selected tag; conditions across different filter types apply together. Tap a selected condition's × to remove it, clear all conditions, or tap the same card studio/actor again to toggle it off. Filter dialogs support searching options and clearing that filter type.
- **Background metadata:** the catalog appears first while studio, actor, and tag information loads in the background and is cached per server. The app tries the read-only REST list, then falls back to DeoVR details when restricted. It does not send player credentials to the REST metadata interface. Refresh retries metadata retrieval when information is incomplete.
- **Cover recovery:** the app tries XBVR's web image proxy, then the original image and alternate covers. Failed covers offer tap-to-retry. Image downloads are limited to 12 MB and decoded at a reduced size; successful covers still display completely and keep their aspect ratio.
- **Local views:** Library, Continue watching, and Favorites show the catalog, local viewing records, and local favorites. The selected navigation item is highlighted. Long-press a poster to add or remove a local favorite.
- **Resume and return:** tap a poster to restore the selected file and playback position. Returning to the library retains the search, category, and browsing position.
- **Theme and configuration:** theme changes and rotation retain the query, category, applied filters, browsing position, and connection draft without reloading the catalog merely because the configuration changed.
- **Clear states:** loading, connection failures, empty searches, no viewing history, and no favorites have dedicated messages. A cached catalog can remain visible after a failed refresh; playback still requires access to the server and media.

## Player

Video fills the player window. Top and bottom gradient overlays extend to the screen edges, while controls and text avoid cutouts, notches, and system gesture areas. The header, central transport controls, and bottom actions remain separate; phone landscape mode uses a compact layout. Library and settings dialogs follow the system theme.

- **Header:** return to the library, switch portrait/landscape, or open More playback settings.
- **Transport and gestures:** play/pause and seek backward/forward **10 seconds**. Drag the VR picture to look around and pinch to zoom. Double-tap the left or right side of the picture to seek backward or forward 10 seconds.
- **Bottom actions:** time, seek bar, and four icon shortcuts: **Format / Files / Gyro / Reset**, with localized accessibility names and long-press tooltips. Gyro is white when off and yellow when successfully enabled, and is off by default. Reset restores the viewing direction and default field of view. Format contains projection, stereo layout, and viewing-eye selection: left/right for SBS, top/bottom for TB, and no eye switch for MONO.
- **Control visibility:** tap the picture to show or hide controls. During normal playback they hide after approximately **4.2 seconds**. They remain visible while paused, while dragging the seek bar, or while a settings dialog is open.
- **File switching:** retains the current position and play/pause state. The app does not create lower-resolution copies or request server transcoding.
- **Leaving playback:** returning to the library or putting the app in the background saves progress and releases decoding and streaming resources.

More playback settings groups its actions under Playback, View, and Media:

| Setting | Purpose |
| --- | --- |
| Chapters | Show server-provided timestamps and jump to them; report when none are available |
| Playback speed | Select playback speed |
| Audio and subtitles | Select device-supported tracks; subtitles must be provided by the server or present as usable media tracks |
| Lens calibration | Adjust fisheye center, radius, rotation, and mirroring; adjust stereo packing for flat video |
| Favorites | Change a local favorite, or a server favorite when permission is explicitly available |
| Play from beginning | Seek to the beginning |

Progress is saved approximately every five seconds and when pausing, leaving, or entering the background. Local records use the server and stable scene/file identity, helping keep resume history intact when media URL query parameters change. Clearing app data or uninstalling deletes local server profiles, favorites, and viewing records.

## Playback diagnostics

If **Playback could not start** appears, select **View diagnostics → Copy report**. If the app still exits directly to the home screen, reopen it and choose **Playback diagnostics → Copy report** from the library's server menu, then share that report with the developer.

Reports stay on the device and include the device, OS, app version, exception type and stack, available GPU-stage information, and system exit reasons. They do not save exception messages or raw exit traces and are not uploaded automatically. Java exception handling cannot guarantee recovery from a native graphics-driver crash.

## Projection and format correction

Seven projection types each support MONO, SBS, and TB, for **all 21 combinations**. Single-eye cropping takes place during projection mapping.

| Projection | Mono (MONO) | Side by side (SBS / LR) | Top and bottom (TB) |
| --- | --- | --- | --- |
| Flat | Supported | Supported | Supported |
| 180° equirectangular panorama | Supported | Supported | Supported |
| 360° equirectangular panorama | Supported | Supported | Supported |
| 180° equidistant fisheye | Supported | Supported | Supported |
| 190° equidistant fisheye | Supported | Supported | Supported |
| 200° equidistant fisheye | Supported | Supported | Supported |
| 220° equidistant fisheye | Supported | Supported | Supported |

The supported names include the 13 reference modes: `180_LR`, `FISHEYE_180_LR`, `FISHEYE_190_LR`, `FISHEYE_200_LR`, `FISHEYE_220_LR`, `360_LR`, `360_TB`, `180_MONO`, `360`, `FISHEYE_200`, `FISHEYE_220`, `SBS_MONO`, and `NONE`. The legacy name `SBS_MONO` means viewing one eye of a flat SBS video.

Detection gives priority to saved manual settings, followed by reliable metadata for the selected file, then lens/layout markers in the filename. Explicit file modes such as `360_tb` are not overridden by a generic scene layout. Specific markers such as `RF52`, `MKX200`, `MKX220` / `MKX22`, and `VRCA220` can refine generic projection information. Unknown formats ask for confirmation and remain manually selectable. Cube, EAC, and unstitched front/back dual-fisheye 360° video are not currently unfolded.

If the picture's projection or layout is wrong, open **Format**, choose the projection, then choose the layout. Changing projection does not rebuild the network player and preserves position and pause state. **Restore automatic detection** removes the manual format override.

**More → Lens calibration** adjusts and saves each file's per-eye fisheye center, radius, rotation, and horizontal mirror. Radius 1 is half the shorter edge of the per-eye image; the center uses per-eye UV coordinates from 0 to 1. Flat stereo video offers full or half packing. Explicit filename markers `FULL_SBS` / `FSBS` / `FULL_TB` / `FTB` select full packing; other flat stereo video defaults to half packing and can be corrected manually.

The 190° fisheye preset uses a 95° lens half-angle. RF52 detection selects a **generic 190° equidistant preset**, with **no dedicated RF5.2mm lens calibration**. The 200° and 220° presets also use the generic equidistant model; they do not claim lens-specific nonlinear correction.

## Favorites and stored data

Local favorites are always available, do not write to the server, and appear in the Favorites library view. The player's server-favorite option appears only when HereSphere details explicitly declare `writeFavorite` permission. It sends a change only after the user selects that option, then rereads the result to confirm it. Refresh the catalog to update server-favorite playlist groups.

Server usernames and passwords are encrypted with an Android Keystore key rather than saved as ordinary plaintext preferences. Player JSON requests do not forward credentials across server origins. Proxy Basic authentication is attached only to requests matching the configured server origin. The app has no video deletion, server-settings modification, or transcoding actions.

## Device requirements and validation limits

Decoding depends on the device's Android media codecs. The codec, bit depth, resolution, and frame rate inside MP4, MKV, or other containers must be supported by the device. If playback fails, choose an existing compatible file under **Files**.

An 8K stereo video still requires decoding its complete frame when displaying only one eye; cropping does not reduce the decoder's frame size. High-bitrate playback also depends on network throughput and the backend's ability to read the file. 8K, high bit depth, HDR, long-term audio/video synchronization, real-device colors, and physical orientation-sensor behavior require separate device tests. Recognized HDR sources currently display a color-validation warning; SDR is recommended until those colors have been verified.

Gyro is off by default. Devices without a supported rotation-vector sensor can still use touch controls. Reset recenters the view and restores the default viewing field of view. This app is for **single-eye viewing on phones and tablets**; it has no Quest/PICO headset stereo mode, spatial interaction, or dedicated automatic lens calibration.

The recorded emulator checks use synthetic scenes and a local test server. Emulator screenshots, simulated tablet dimensions, mathematical tests, and shader fault injection are useful software evidence, but do not establish real headset support, physical sensor feel, real-server cover compatibility, or device-specific decoding and color performance. Consult [VALIDATION.md](VALIDATION.md) for what has actually been exercised on the relevant release.

## Build from source

Toolchain: **AGP 8.13.2**, **Gradle 8.13**, **JDK 17**, Android **compileSdk / targetSdk 36**, **minSdk 29**, and **Media3 1.8.0**. The repository includes the Gradle Wrapper, which can download Gradle and Maven dependencies when network access is available.

Source distributions exclude the local toolchain, user data, private caches, signing private key, and `local.properties`. Prepare JDK 17, Android SDK Platform 36, Build Tools 36.0.0, and Platform Tools, and accept the SDK licenses. On Windows, use a short directory path without spaces, for example `<project-directory>`.

Create `local.properties` in the project root using your own SDK path. Forward slashes avoid Windows backslash escaping:

```properties
sdk.dir=<YOUR_ANDROID_SDK_PATH>
```

In PowerShell, set the actual JDK path and run:

```powershell
$env:JAVA_HOME = '<YOUR_JDK_17_PATH>'
.\build.ps1
```

The script runs `assembleDebug`, `testDebugUnitTest`, and `lintDebug` by default. To build only the APK:

```powershell
.\build.ps1 -Tasks assembleDebug
```

The APK is written to `app\build\outputs\apk\debug\app-debug.apk`. When `toolchain\debug.keystore` does not exist, `build.ps1` creates a local development signing key and builds with it. These are development APKs; store-release signing is not configured.

After setting up the SDK, JDK, and development keystore, the Gradle Wrapper can also be invoked directly:

```powershell
.\gradlew.bat --no-daemon assembleDebug testDebugUnitTest lintDebug
```

**Keep the same build machine's `toolchain\debug.keystore` for subsequent updates.** A newly generated key on another machine does not match the distributed APK, so its APK cannot replace that installation directly. Uninstalling to install a differently signed APK removes local data. The signing private key is not included with the source or shared publicly. Continuous updates must be built by the machine holding the original key, or after a deliberate secure key migration.

With Android Platform Tools and USB debugging available, install using:

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

`CoreTest` covers protocol handling, time units, stable identities, format detection, and projection mathematics. `ApiSecurityTest` uses a local MockWebServer for redirect credential isolation. Rendering tests cover direction, aspect calculations, and shader behavior. Use [VALIDATION.md](VALIDATION.md) for executed results and emulator versus real-device coverage.

## Acknowledgments and project relationship

Playback uses [Google AndroidX Media3 / ExoPlayer](https://github.com/androidx/media) **1.8.0** for media decoding, playback state, audio tracks and subtitles. Thanks to its maintainers and contributors. Media3 is licensed under [Apache-2.0](https://github.com/androidx/media/blob/release/LICENSE). VR projection and touch/gyro view control are implemented by this project's OpenGL ES renderer. See [third-party notices](THIRD_PARTY_NOTICES.md) for the dependency list.

Thanks to the [XBVR project](https://github.com/xbapps/xbvr) for the media-server project and player interfaces this client uses.

**XBVR-Mobile preview player is an independent Android client.** It is not a Git-history fork of the upstream XBVR repository and is not presented as an official XBVR application. Linking and crediting XBVR does not imply upstream endorsement.

## License

See [Privacy](PRIVACY.md) and [Security reporting](SECURITY.md). GitHub Actions runs tool regression tests, JVM unit tests and Android lint on source changes; actual emulator and device results remain documented in [VALIDATION.md](VALIDATION.md).

Project-authored code and documentation that the project has authority to license use [Apache-2.0](LICENSE). Third-party components, underlying screenshot media and trademarks remain outside that grant. See [license scope](licenses/LICENSE_SCOPE.md), [third-party notices](THIRD_PARTY_NOTICES.md) and [source provenance](docs/compliance/SOURCE_PROVENANCE.md).

Use the server menu’s Open-source licenses entry to read the full terms offline. desugar_jdk_libs retains GPLv2 with the Classpath Exception; [release source](licenses/SOURCE_AVAILABILITY.md) is supplied alongside the APK. The historical fish-eye userscript was not located; provenance review does not guarantee zero copying.

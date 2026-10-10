# Settings Navigation Implementation Plan

**Goal:** Replace the toolbar server dialog with Settings → Media library settings, retaining existing connection and removal behavior while keeping the library underneath intact.

**Architecture:** MainView hosts a full-page SettingsView overlay. SettingsNavigation owns only the page route; server persistence and loading remain in MainActivity/LibraryController. Connection, ratio and removal confirmations remain dialogs above the settings page. Pages rebuild on configuration changes and restore their route after process recreation.

**Tech Stack:** Java 17, native Android Views, JUnit 4, adb real-device checks.

- [x] Verify Git tracking and preserve existing removal work on feat/settings-navigation.
- [x] Add route tests covering hierarchical back, invalid saved routes and no implicit navigation during server operations; run before production changes.
- [x] Implement SettingsNavigation and SettingsView: root summary, library server list with per-row overflow, current-server ratio, loading/error/retry, About/version/licenses.
- [x] Integrate the gear entry and overlay with MainView, Activity back dispatch, saved state and rotation. Keep the grid mounted; suppress automatic connection popup after deleting the last profile in settings.
- [x] Update Chinese/English strings and README navigation instructions.
- [x] Run JVM tests, lint and release assembly; inspect diff and APK signature.
- [x] Install candidate on available authorized phone; verify Settings, library settings, overflow cancellation, ratio and nested back, rotation, and absence of crashes without deleting real server profiles.

Acceptance: Settings entry is a gear; app-wide diagnostics/licenses are outside media library settings. Both toolbar and system back follow the hierarchy. Editing/canceling returns to the same page. Switching and removing servers refresh the list without closing settings. No-server state offers Add server. Per-server ratios retain their persistence. Opening and closing settings leaves the existing library filters and scroll unchanged.

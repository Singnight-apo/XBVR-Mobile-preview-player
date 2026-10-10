# Matching File Preview Implementation Plan

> Implement sequentially with the existing approved scope; preserve the feature branch.

**Goal:** Preview the selected file from matching screens without losing context.

**Architecture:** A preview-only MediaDetailsRepository loads file-specific DeoVR metadata through XbvrApi. PlayerActivity selects it only for an explicit internal preview intent. ManagementView captures state and launches the existing player.

**Tech Stack:** Java, native Android views, existing Media3/OpenGL player, OkHttp, MockWebServer/JUnit.

- [x] Add failing mock-server tests for file-specific URL, correct file identity, dnt and isolated authentication; test URL construction validation.
- [x] Add preview repository, composition-root factory and PlayerActivity mode selection. Add localized preview buttons on candidate/comparison/details pages.
- [x] Run JVM tests, lint and release build. Install without clearing data.
- [x] Extend isolated synthetic fixture with range-readable video; test preview, seek, rotation, return context and errors on Xiaomi 13.
- [x] Preserve user's server and settings, record evidence and commit only to feature branch.

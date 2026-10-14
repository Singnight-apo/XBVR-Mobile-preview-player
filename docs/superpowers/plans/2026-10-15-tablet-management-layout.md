# Tablet management layout implementation plan

> **For agentic workers:** Use superpowers:executing-plans to implement the following tasks sequentially. No delegation is needed.

**Goal:** Implement the approved responsive tablet management layout while preserving existing operations.

**Architecture:** A pure Java layout/navigation policy defines phone, tabs and sidebar modes. ManagementView composes the existing content inside the selected shell and updates selected navigation without replacing API or mutation handlers.

**Tech Stack:** Java, Android native Views, JUnit 4, Gradle, ADB.

### Task 1: Layout/navigation policy
- [x] Add ManagementLayoutTest covering phone, tablet portrait/landscape, narrow split window, matching subroutes and top-level back behavior.
- [x] Run targeted test to observe expected failure, implement ManagementLayout, rerun to pass.

### Task 2: Responsive shell
- [x] Add sidebar/top navigation and tablet default section in ManagementView; preserve phone create/back paths.
- [x] Synchronize active section on route changes and configuration restoration; use localized labels and existing palette/icons.

### Task 3: Maintenance content
- [x] Group tablet directory rows; adapt timestamp position and supporting actions to available layout.
- [x] Keep capability checks, uncertainty guards and confirmation callbacks unchanged.

### Task 4: Verification and handoff
- [x] Run full JVM tests, lint and signed release build; verify resource parity and certificate.
- [x] Cover-install candidate on tablet, inspect horizontal/vertical screenshots and navigation, then restore rotation/language.
- [x] Preserve unrelated build.ps1 change; keep work on feature branch and do not release.

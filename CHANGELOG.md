# Changelog

All notable changes to this project will be documented in this file.

## Unreleased

## 2.0.0 - 2026-04-14

### Added
- Dedicated `Scan` mode with live QR scanning using CameraX and ML Kit.
- Dedicated `History` mode with persistent local scan history across app restarts.
- Support for additional QR content types in generation and scanning:
  - Wi-Fi
  - phone
  - email
  - SMS
  - location
  - contact
- QR sharing through Android's native share sheet.
- Multiple save/export formats: PNG, JPEG, and WEBP.
- Custom QR design options including style presets, foreground/background colors, finder eye styles, center badges, and optional center logo support.
- Spanish localization support across the main app flows.
- Local telemetry and crash logging foundations.
- GitHub workflows for Android CI, linting, release artifact builds, dependency checks, wrapper validation, and Dependabot updates.
- A home screen widget for quick QR generation from clipboard text.
- Export and import support for scan history with merge/replace flows.
- Debug-only performance tracing with Logcat output and an optional hidden in-app panel.

### Changed
- Modernized the main UI with improved spacing, labels, buttons, and better phone/tablet layout behavior.
- Replaced the typed QR color field with a color picker and added defaults for selection fields.
- Added dark mode support across the app.
- Improved accessibility with better labels, headings, status messaging, and screen-reader-friendly announcements.
- Improved the scanner flow with duplicate-scan throttling, `Scan Again`, and `Use in Create`.
- Added richer Wi-Fi QR handling for both generated and scanned QR codes.
- Tuned scanner battery usage with lifecycle-aware shutdown, lower analysis workload, and inactivity auto-pause.
- Optimized QR generation by reusing the writer, caching decoded logos, skipping unchanged rerenders, and using faster bitmap population.
- Cleaned deprecated Gradle and Android Gradle Plugin configuration and updated project workflow setup.
- Updated `.gitignore` to better protect secrets and local-only files.
- Updated the project README for the `2.0.0` app scope and release process.

### Security
- Added suspicious-link detection for scanned URLs, including warnings for punycode domains, shorteners, raw IP hosts, unusual ports, hidden user info, odd hosts, and excessive subdomains.
- Added review dialogs for suspicious link actions before opening them.
- Added review dialogs for risky phone and SMS payloads before calling or messaging.
- Added Wi-Fi security warnings for open and WEP networks during scanned Wi-Fi connect flows.
- Replaced deprecated accessibility announcement usage with a live-region-based approach.

### Fixed
- Restored the missing Gradle wrapper so the project builds reliably again.
- Fixed exported QR save behavior so generated images save in valid, renderable formats.
- Cleaned remaining scanner-related build warnings in the recent scan/security code paths.

# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Nothing yet.

## [5.0.0] — 2026-09-22

### Added
- Initial public release of Emergency Intake System.
- `HospitalServer`: TCP socket server (port 9090) with a weighted differential-diagnosis
  (DDX) engine covering 24 candidates across cardiac, neurological, respiratory,
  systemic, paediatric, and OB-GYN categories.
- Age, gender, and symptom-exclusion modifiers on diagnosis candidates.
- Onset-duration scoring modifier (`SUDDEN` → `MONTHS`) to bias urgency realistically.
- `EmergencyClientFX`: JavaFX desktop client with:
  - Intake form (patient info, symptom grid, onset selector)
  - Per-diagnosis result modal with match-score bars and symptom fingerprinting
  - Patient Database tab with live search, urgency filtering, and record detail view
  - Clipboard-exportable plain-text patient report
- Persistent JSON patient database at `~/hospital_records.json`.
- Maven build (`pom.xml`) producing a runnable fat JAR via `maven-shade-plugin`.
- GitHub Actions CI (build + test matrix on JDK 17 and 21).

[Unreleased]: https://github.com/mustafa-codez/EIS-v5.0/compare/v5.0.0...HEAD
[5.0.0]: https://github.com/mustafa-codez/EIS-v5.0/releases/tag/v5.0.0

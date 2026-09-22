# Contributing to Emergency Intake System

Thanks for your interest in improving EIS! This is an educational project demonstrating
Java networking, rule-based scoring systems, and JavaFX UI design — contributions of all
sizes are welcome, from fixing a typo to proposing new diagnosis candidates.

## Before you start

- Check [open issues](https://github.com/mustafa-codez/EIS-v5.0/issues) to avoid duplicate work.
- For anything non-trivial (new features, architectural changes), please open an issue
  first to discuss the approach before writing code.
- This project has **zero clinical validity** by design — see the [Disclaimer](README.md#️-disclaimer).
  Contributions should preserve that framing; this is not, and will not become, a real
  diagnostic tool.

## Development setup

```bash
git clone https://github.com/mustafa-codez/EIS-v5.0.git
cd EIS-v5.0
mvn clean install
mvn javafx:run
```

See the [README](README.md#-getting-started) for full prerequisites.

## Project structure

```
src/main/java/com/eis/
├── server/HospitalServer.java   # TCP server, DDX engine, JSON persistence
└── client/EmergencyClientFX.java # JavaFX UI
src/test/java/com/eis/           # Unit tests
```

## Making changes

1. Fork the repo and create a branch off `main`:
   `git checkout -b feature/short-description`
2. Make your changes, following the existing code style (see below).
3. Add or update tests under `src/test/java` where relevant — especially for changes to
   `HospitalServer.runDDX()` or scoring logic.
4. Run the full build before opening a PR:
   ```bash
   mvn clean verify
   ```
5. Commit using clear, descriptive messages (imperative mood, e.g. `Add POTS exclusion for chest pain`).
6. Open a pull request against `main` using the PR template — fill it in completely.

## Code style

- Java 17, standard 4-space indentation.
- Keep `HospitalServer` (business logic / protocol) and `EmergencyClientFX` (UI) cleanly
  separated — avoid adding UI code to the server class or vice versa.
- Favor small, well-named private helper methods over long inline blocks in the UI code
  (see existing `mk()`, `sBtn()`, `secLbl()` helpers for the established pattern).
- New disease candidates go in `HospitalServer.CANDIDATES` — keep the weight/threshold
  rationale in a code comment or in your PR description.

## Adding a new diagnosis candidate

Each `DxCandidate` needs:

| Field | Notes |
|---|---|
| `medicalName` / `commonName` | Medical term + a patient-friendly name |
| `urgency` | One of `EMERGENCY`, `SERIOUS`, `SAFE` |
| `action` | Concise clinical action string |
| `symptoms` / `weights` | Parallel arrays — weight reflects diagnostic significance |
| `threshold` | Minimum summed score before the candidate is even considered |
| `minAge` / `maxAge` | Use `-1` for no limit |
| `gender` | `"male"`, `"female"`, or `"any"` |
| `excludeIfAll` | Symptoms that, if ALL present, rule this candidate out |

Also add a short entry to `dxProfile()` in `EmergencyClientFX.java` so the result card
gets a sensible icon/category/accent color, and update the candidate table in the README.

## Reporting bugs / requesting features

Please use the issue templates — they ask for exactly what's needed to reproduce a bug
or evaluate a feature request efficiently.

## Code of Conduct

This project follows the [Contributor Covenant](CODE_OF_CONDUCT.md). By participating,
you're expected to uphold it.

## License

By contributing, you agree that your contributions will be licensed under the project's
[MIT License](LICENSE).

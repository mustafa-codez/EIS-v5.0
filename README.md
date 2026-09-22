# 🏥 Emergency Intake System (EIS)

<p align="left">
  <a href="https://github.com/mustafa-codez/EIS-v5.0/actions/workflows/ci.yml"><img src="https://github.com/mustafa-codez/EIS-v5.0/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <img src="https://img.shields.io/badge/Java-17%2B-orange?logo=openjdk" alt="Java 17+">
  <img src="https://img.shields.io/badge/JavaFX-21.0.2-blue?logo=java" alt="JavaFX 21">
  <img src="https://img.shields.io/badge/build-Maven-C71A36?logo=apachemaven" alt="Maven">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-green" alt="MIT License"></a>
  <a href="CONTRIBUTING.md"><img src="https://img.shields.io/badge/PRs-welcome-brightgreen" alt="PRs welcome"></a>
</p>

> ⚠️ **Educational demo only — not for clinical use.** See [Disclaimer](#️-disclaimer).

A real-time emergency triage desktop application built with **Java + JavaFX**. EIS uses
a local socket-based client–server architecture, a weighted differential-diagnosis (DDX)
engine, and a persistent JSON patient database — all wrapped in a dark-mode monospace UI.

---

## Table of Contents

- [Features](#-features)
- [Screenshots](#-screenshots)
- [Architecture](#-architecture)
- [DDX Engine](#-ddx-engine)
- [Getting Started](#-getting-started)
- [Socket API](#-socket-api)
- [Project Structure](#-project-structure)
- [Testing](#-testing)
- [Roadmap](#-roadmap)
- [Contributing](#-contributing)
- [Disclaimer](#️-disclaimer)
- [License](#-license)

---

## ✨ Features

| Feature | Description |
|---|---|
| **DDX Engine** | Weighted symptom scoring across 24 disease candidates — cardiac, neuro, respiratory, systemic, paediatric, and gender-specific |
| **Onset Modifiers** | Sudden / Hours / Days / Weeks / Months onset shifts scores toward clinically plausible urgency levels |
| **Age & Gender Filters** | Candidates automatically excluded outside their valid age range or gender (e.g. POTS in young females, aortic dissection in older males) |
| **Exclusion Logic** | `excludeIfAll` prevents symptom patterns from colliding (e.g. musculoskeletal chest pain excluded when arm numbness + rapid heartbeat present) |
| **Live Socket Server** | `HospitalServer` binds port `9090`; launched automatically in a background thread when the JavaFX app starts |
| **JSON Patient Database** | Records saved to `~/hospital_records.json`; queried and filtered in the Database tab |
| **Per-Disease Visual Identity** | Each diagnosis card has its own icon, category label, and accent colour (cardiac red, neuro purple, respiratory teal, etc.) |
| **Clipboard Report** | One-click plain-text patient report copied to the system clipboard |
| **Live Clock & Status Bar** | Real-time clock, server online/offline status, and transmit-state indicator |
| **Continuous Integration** | Every push is built and tested on JDK 17 and 21 via GitHub Actions |

---

## 🖼 Screenshots

> Add screenshots to `docs/` and they'll render automatically below.

| Intake Form | Result Modal | Patient Database |
|---|---|---|
| ![Intake form](docs/intake.png) | ![Result modal](docs/result.png) | ![Patient database](docs/database.png) |

---

## 🏗 Architecture

```
┌─────────────────────────────────────┐
│      com.eis.client                 │  JavaFX desktop UI
│      EmergencyClientFX              │
│  ┌──────────────┬─────────────────┐ │
│  │  Intake Form │  Patient DB Tab │ │
│  └──────┬───────┴────────┬────────┘ │
│         │ TCP :9090      │ TCP :9090│
└─────────┼────────────────┼──────────┘
          │                │
┌─────────▼────────────────▼──────────┐
│      com.eis.server                 │  Java TCP server
│      HospitalServer                 │
│  ┌────────────┐  ┌────────────────┐ │
│  │ DDX Engine │  │ JSON DB (file) │ │
│  └────────────┘  └────────────────┘ │
└─────────────────────────────────────┘
```

**Protocol:** newline-terminated JSON objects over raw TCP.
**Database:** `~/hospital_records.json` — a JSON array appended on every `SAVE` action.

---

## 🧠 DDX Engine

The scoring pipeline in `HospitalServer.runDDX()`:

1. **Age filter** — candidates with `minAge`/`maxAge` constraints are skipped if the patient falls outside the range.
2. **Gender filter** — `"male"` / `"female"` candidates are excluded for non-matching patients; `"any"` candidates always pass.
3. **Exclusion check** — if **all** symptoms in a candidate's `excludeIfAll` list are present, the candidate is dropped.
4. **Symptom scoring** — each matched symptom contributes its weight; unmatched = 0.
5. **Threshold gate** — candidates scoring below their `threshold` are dropped.
6. **Onset modifier** — `SUDDEN` onset adds +20 to EMERGENCY candidates and −15 to SAFE; `MONTHS` adds +12 to SAFE and −25 to EMERGENCY; etc.
7. **Top 3 returned** — sorted descending by score; labelled HIGH / MODERATE / LOW probability.

This logic is covered by unit tests in [`HospitalServerTest`](src/test/java/com/eis/server/HospitalServerTest.java).

### Disease Knowledge Base (24 Candidates)

| Category | Conditions |
|---|---|
| **Cardiac** | Myocardial Infarction, Unstable Angina, Acute Pericarditis, SVT, Hypertensive Emergency, Aortic Dissection, POTS |
| **Neurological** | Ischaemic Stroke, TIA, Status Epilepticus, Febrile Seizure, Migraine with Aura, Subarachnoid Haemorrhage, Tension Headache, Meningococcal Meningitis |
| **Respiratory / Allergic** | Anaphylaxis, Acute Severe Asthma, Pulmonary Embolism |
| **Systemic / Other** | Sepsis / Septic Shock, Vasovagal Syncope, Musculoskeletal Chest Pain |
| **Paediatric** | Kawasaki Disease, Intussusception, Febrile Seizure |
| **OB-GYN** | Ectopic Pregnancy Rupture |

Want to add a candidate? See [CONTRIBUTING.md](CONTRIBUTING.md#adding-a-new-diagnosis-candidate).

---

## 🚀 Getting Started

### Prerequisites

| Requirement | Version |
|---|---|
| JDK | 17+ |
| Maven | 3.8+ |

> JavaFX and Jackson are pulled automatically by Maven — no manual SDK setup required.

### Clone & Run

```bash
git clone https://github.com/mustafa-codez/EIS-v5.0.git
cd EIS-v5.0
mvn javafx:run
```

`HospitalServer` starts automatically in a background thread when the app launches —
there's nothing separate to run.

### Build a standalone JAR

```bash
mvn clean package
java -jar target/emergency-intake-system-5.0.0.jar
```

The Maven Shade plugin bundles all dependencies into a single runnable JAR under `target/`.

### IntelliJ IDEA

1. Open the project — IntelliJ will detect the `pom.xml` and resolve dependencies automatically.
2. Run the `EmergencyClientFX` main method directly, or use the Maven `javafx:run` goal.

---

## 📡 Socket API

All requests and responses are **compact JSON**, newline-terminated, over TCP port 9090.

### Triage Request

```json
{
  "name": "John Smith",
  "age": 58,
  "gender": "male",
  "chiefComplaint": "Crushing chest pain radiating to the left arm",
  "onset": "SUDDEN",
  "symptoms": ["chest pain", "arm numbness", "rapid heartbeat", "lightheaded"]
}
```

### Triage Response

```json
{
  "name": "John Smith",
  "age": 58,
  "gender": "male",
  "onset": "SUDDEN",
  "urgency": "EMERGENCY",
  "diagnoses": [
    {
      "medicalName": "Myocardial Infarction (STEMI/NSTEMI)",
      "commonName": "Heart Attack",
      "urgency": "EMERGENCY",
      "action": "12-lead ECG immediately. Aspirin 300mg. Activate cath lab. IV access.",
      "score": 95,
      "category": "CARDIAC",
      "matchedSymptoms": ["chest pain", "arm numbness", "rapid heartbeat", "lightheaded"],
      "probability": "HIGH"
    }
  ]
}
```

### Save Record

```json
{ "action": "SAVE", "record": { /* triage response object */ } }
```

Response: `{"status":"OK"}`

### Query All Records

```json
{ "action": "QUERY_ALL" }
```

Response: JSON array of all saved records.

---

## 🗂 Project Structure

```
EIS-v5.0/
├── .github/
│   ├── workflows/ci.yml          # Build + test on JDK 17 & 21
│   ├── ISSUE_TEMPLATE/           # Bug report & feature request forms
│   ├── PULL_REQUEST_TEMPLATE.md
│   └── CODEOWNERS
├── src/
│   ├── main/java/com/eis/
│   │   ├── server/HospitalServer.java     # TCP server + DDX engine + JSON database
│   │   └── client/EmergencyClientFX.java  # JavaFX client — intake form, result modal, DB tab
│   └── test/java/com/eis/server/
│       └── HospitalServerTest.java        # Unit tests for the DDX engine
├── docs/                          # Screenshots referenced in this README
├── pom.xml                        # Maven build (fat-jar via shade plugin)
├── CONTRIBUTING.md
├── CODE_OF_CONDUCT.md
├── SECURITY.md
├── CHANGELOG.md
├── LICENSE
└── README.md
```

At runtime, `~/hospital_records.json` is created automatically as the patient database.

---

## ✅ Testing

```bash
mvn test
```

Unit tests in [`HospitalServerTest`](src/test/java/com/eis/server/HospitalServerTest.java)
cover:
- Correct top-candidate selection for classic presentations (e.g. MI)
- Empty-symptom input returning no results
- Age and gender exclusion filters
- Result capping (max 3) and descending score order
- Non-negative scores after onset modifiers

CI runs this matrix automatically on every push and pull request — see the badge at the
top of this README.

---

## 🗺 Roadmap

- [ ] Screenshots in `docs/`
- [ ] Optional persistence backend (SQLite) behind the same `HospitalServer` protocol
- [ ] Export patient report as PDF, not just clipboard text
- [ ] Configurable disease knowledge base (external JSON/YAML instead of hardcoded array)
- [ ] Basic auth / TLS on the socket server for non-localhost demos

Have an idea? Open a [feature request](../../issues/new?template=feature_request.md).

---

## 🤝 Contributing

Contributions are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md) for setup instructions,
code style, and how to propose a new diagnosis candidate. Please also read the
[Code of Conduct](CODE_OF_CONDUCT.md).

Found a security issue? See [SECURITY.md](SECURITY.md) rather than opening a public issue.

---

## ⚠️ Disclaimer

This project is an **educational demonstration** of socket programming, rule-based
scoring systems, and JavaFX UI design.

- It is **not validated** for medical use.
- Symptom weights and thresholds are illustrative, not evidence-based.
- **Do not use this software to make any clinical or diagnostic decisions.**

---

## 📄 License

MIT License — see [LICENSE](LICENSE) for details.

---

*Built as a portfolio project demonstrating Java networking, rule-based AI, and modern JavaFX UI design.*

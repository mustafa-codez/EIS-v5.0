# Security Policy

## Scope

Emergency Intake System (EIS) is an **educational demo project**. It is not deployed as
a production service, does not handle real patient data, and is not intended for
clinical use. That said, the socket server and local file handling are real code paths,
and security reports are welcome.

Known scope limitations, by design:

- `HospitalServer` binds to `127.0.0.1:9090` with **no authentication or encryption** —
  it is intended for local, single-machine use only. Do not expose this port publicly.
- Patient records are stored in plaintext JSON at `~/hospital_records.json` with no
  encryption at rest.

## Supported Versions

| Version | Supported |
|---|---|
| 5.0.x   | ✅ |
| < 5.0   | ❌ |

## Reporting a Vulnerability

If you discover a security issue (e.g. a deserialization bug, path traversal, or a way
to crash/exploit the socket server), please report it privately rather than opening a
public issue:

1. Go to the [Security tab](https://github.com/mustafa-codez/EIS-v5.0/security) of this
   repository and use **"Report a vulnerability"**, or
2. Open a [GitHub Discussion](https://github.com/mustafa-codez/EIS-v5.0/discussions)
   marked private, if advisories aren't enabled.

Please include:
- A description of the vulnerability and its potential impact
- Steps to reproduce (a minimal repro is very helpful)
- Any suggested remediation, if you have one

I'll do my best to acknowledge reports within a few days. As this is a solo educational
project without a formal SLA, response times may vary.

## Disclosure

Given the project's educational nature and lack of production deployment, coordinated
disclosure timelines are flexible — please just give a reasonable window for a fix
before any public write-up.

# Security Policy

## Supported Versions

Security fixes are applied to the latest development line of `polyglot-adapter` on `main` and, when needed, to the most recent supported release branch.

At the moment:

- `main`: supported
- latest published release line: supported for critical fixes when maintainable
- older release lines: not supported unless explicitly stated in release notes

## Reporting a Vulnerability

Do not open a public GitHub issue for an undisclosed vulnerability.

Instead, report security issues privately through one of these channels:

- GitHub Security Advisories for this repository
- direct maintainer contact listed in the repository metadata

Include:

- affected module or artifact
- affected versions
- reproduction details or proof of concept
- impact assessment
- any suggested remediation if available

## Response Expectations

- Initial triage target: within 5 business days
- Status updates: provided during investigation when the report is actionable
- Fixes: released according to severity, exploitability, and compatibility risk

## Scope

Security reports are especially relevant for:

- code generation that can produce unsafe or unexpected output
- script loading and execution boundaries
- Spring Boot auto-configuration exposure
- dependency vulnerabilities in published artifacts

Best-effort reports about documentation or sample-only issues are still welcome, but production-impacting vulnerabilities take priority.

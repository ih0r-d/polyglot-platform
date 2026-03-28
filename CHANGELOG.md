# polyglot-adapter changelog

All notable changes to **polyglot-adapter** are documented in this file.

## [0.1.0] - 2025-12-17


### ➕ Added
- initial Spring Boot starter for polyglot-adapter (db83b77)
- create demo with spring boot starter (a113375)


### ⚙️ Changed
- move to multi-module layout with BOM (6ccb2c2)


### ✅ Fixed
- update dependency org.bouncycastle:bcpg-jdk18on to v1.82 (6dcd747)
- stabilize library logic with small refactoring (67899d8)
- update java example with using new library API (a3da9c5)
- update dependency org.bouncycastle:bcpg-jdk18on to v1.83 (f4467e1)
- small update and cleanup core module (a63cdfa)


### 🧪 Internal
- update release script with creation CHANGELOG.md (0103ee0)
- update Readme.md (f17605f)
- update actions/cache action to v5 (113b288)
- update maven dependencies (51c7dab)
- update dependency maven to v3.9.12 (7444e0f)
- update spring boot starter documentation (945e7d2)
- fix maven pom files (7818f48)
- update root documentation file (100a845)
- update release script (c85857c)


## [Unreleased]


### ⚙️ Changed
- make `@PolyglotClient.convention()` active in runtime binding and validation; add `BY_METHOD_NAME`, keep `DEFAULT` backward-compatible for invocation, and fail explicitly for unsupported JavaScript `BY_INTERFACE_EXPORT`
- make Spring starter properties real for `0.2.0`: support startup preload, eager fail-fast client validation, package-default client scanning, configurable startup logging, and explicit actuator enablement
- clarify startup preload semantics as raw script evaluation rather than contract prebinding, and add Spring/concurrency stabilization tests


### ✅ Fixed
- update dependency org.bouncycastle:bcpg-jdk18on to v1.82 (6dcd747)
- stabilize library logic with small refactoring (67899d8)
- update java example with using new library API (a3da9c5)


## [v0.0.20] - 2025-11-22


### ➕ Added
- remove PolyglotContextFactory as not needed wrapper of Grall Context (6b7b3f2)


### ✅ Fixed
- update executors (792edf4)


### 🧪 Internal
- update actions/checkout action to v6 (cc7508b)
- update dependency com.diffplug.spotless:spotless-maven-plugin to v3.1.0 (8e68005)
- update java example with new adapter implementation (97cb292)


## [v0.0.19] - 2025-11-09


### ✅ Fixed
- add flattened pom and update release flow (3b9597d)


### 🧪 Internal
- code formatting (24bd507)


## [v0.0.18] - 2025-11-09


### ✅ Fixed
- remove async from executors and update java example (eb08eab)
- update Context build logic (db7c867)


### 🧪 Internal
- improve test coverage (feab0eb)
- integrate Codecov for coverage reporting (e4971e3)
- update maven central release pipeline (27c5a9c)
- fix common utils test (1933b51)
- fix mvn deploy step (e4a9d18)


## [v0.0.17] - 2025-11-08


### ✅ Fixed
- update pom.xml and project example (7a1217b)


## [v0.0.16] - 2025-11-07


### 🧪 Internal
- simplify executors and optimize context configuration (32cac09)


## [v0.0.15] - 2025-11-02


### ⚙️ Changed
- simplify architecture, make language runtimes optional and update documentation (79cca1e)


### 🧪 Internal
- improve test coverage to 64%, code reformat (0c67589)


## [v0.0.14] - 2025-11-02


### 🧪 Internal
- prepare to mvn central release (444f1b7)


## [v0.0.13] - 2025-11-01


### ✅ Fixed
- update maven dependencies (f27d9ec)


## [v0.0.12] - 2025-11-01


### ⚙️ Changed
- remove PolyglotAdapter and migrate to unified binding-based executor API (f729b1a)


## [v0.0.11] - 2025-10-18


### ➕ Added
- implement async evaluation with virtual threads and source caching (56b67c5)


### ⚙️ Changed
- update taskfile, add new java example with numpy (7be82d2)


### 🧪 Internal
- add renovate configuration (584858a)
- add contributing and code of conduct guidelines (e7cebe2)
- update dependency maven to v3.9.11 (28cabc3)
- update dependency org.junit.jupiter:junit-jupiter to v6 (1cbccb1)
- update maven dependencies (3fb55b8)
- update github actions to v5 (d336a11)


## [v0.0.10] - 2025-10-12


### 🧪 Internal
- fix release & bump scripts (2f80eb8)
- remove warnigns from bump script (0371769)


## [v0.0.7] - 2025-10-12


### ⚙️ Changed
- cleanup project structure and code, update context logic and js code executor (e6e3f09)


### 🧪 Internal
- add github action and update release script (f28f0e2)


## [v0.0.5] - 2025-10-10


### ➕ Added
- add simplex method example (6eab970)
- evaluate pure python code logic (737b139)


### 🧪 Internal
- refactor tests and update Taskfile configuration (031271b)


## [v0.0.4] - 2025-10-09


### ➕ Added
- add generic result type (8e0389a)


### ⚙️ Changed
- fix core logic, change project structure (84d057d)


### 🧪 Internal
- add maven wrapper and fix Taskfile (3fdf2cb)
- update Readme.md (37a7a85)


## [v0.0.2] - 2025-10-01


### ➕ Added
- setup project structure, base implementation core module (3fae253)
- add GraalPy plugin with configurable python.packages property (f893049)
- update java-example project, add external library example (58aaa6d)


### ⚙️ Changed
- update project structure, fix java example (594819b)
- remove not valid example pom (5e57cf2)
- update project structure with documentation (4f12e0b)
- update project pom, remove example readme file (fa8aac4)


### ✅ Fixed
- update resources constants for core module, fix java-example module (b55c86c)


### 🧪 Internal
- upgrade core logic and tests, update README (414552d)
- update core README.md (d584ab4)
- add examples block to README.md (5544865)
- add Apache 2.0 license (04d3881)
- configure pom.xml for Maven Central release (cdb17c7)
- update Taskfile, add helper scripts and restructured README with docs (28fa1ab)


---

Generated for **polyglot-adapter** from git history.

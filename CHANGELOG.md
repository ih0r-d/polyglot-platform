# Polyglot-adapter changelog

All notable changes to **polyglot-adapter** (GraalVM polyglot adapter) will be documented in this file.

## [Unreleased]

### ➕ Added
- add Python contract reload helpers and focused real-context lifecycle coverage
- add code generation strict mode, drift-check goal, and doctor goal for Maven workflows
- add repository-level runtime semantics documentation and sample smoke coverage

### ⚙️ Changed
- position `0.3.0` as a Python-first runtime semantics plus codegen reliability release
- align samples and docs with the current `0.3.0-SNAPSHOT` development line
- document SDKMAN/GraalVM 25 as the expected local runtime workflow for repository work

### ✅ Fixed
- clarify preload semantics and cache invalidation behavior across runtime docs and starter guidance
- remove stale sample/version messaging that implied older coordinates as the current line

## [0.2.1] - 2026-03-31

### ➕ Added
- improve startup visibility and demo runtime flags (6883f28)

### ⚙️ Changed
- deduplicate object provider adapters (9ea9ef0)
- fix sonar issues and raise new code coverage (a883d66)

### ✅ Fixed
- align examples with 0.2.0 usage (88974a8)
- defer metrics executor resolution (c99038f)

### 🧪 Internal
- avoid direct use of workflow input in shell (12d6ef8)
- prevent duplicate changelog header on release (33c90fa)
- use conventional commit messages for release scripts (ed29f60)
- normalize published API documentation (3ab0836)
- prepare metadata and skip parent pom publication (6c5cba4)
- verify dry-run publishing flow (f032722)

## [0.2.0] - 2026-03-29


### ➕ Added
- update demo to explicit ScriptSource wiring (731f124)
- add Spring ScriptSource, warmup and metrics support (b9eb0eb)
- improve Python return type inference with heterogeneous fallback (9933aa2)
- add package support and migrate example to generated interfaces (bc83f66)
- integrate polyglot-codegen maven plugin and update examples (752172a)
- support class and dict-style exports (258e9d5)


### ⚙️ Changed
- decouple script loading via ScriptSource SPI (96e3a2c)
- stabilize autoconfig, startup lifecycle, and tests (c52b307)
- update application.yml for spring boot example (4088417)
- extract repeated option values into constants (fb98b3f)
- drop deprecated resource provider in favor of ScriptSource (dca088a)
- introduce contract-api and decouple codegen from runtime (985eafe)
- move codegen and graal to Maven profiles (fb2e7bb)
- unify parsers under LanguageParser and introduce registry dispatch (84ac062)
- refine generated header and checksum format (28e0cbc)
- split codegen into separate parent with contract-api and maven-plugin modules (0deffdb)
- repository structure, build configuration, and documentation cleanup (79ef05b)
- move example project to samples (90c9dbc)
- update docs and github actions (cc45bc7)
- cleanup samples projects (99fb650)
- simplify local automation and document .dev layout (91a10ac)


### ✅ Fixed
- replace field injection with constructor injection in PolyglotClientFactoryBean (7fd6e27)
- add codegen example, small updates on other examples (b477299)
- update maven dependencies (149f6f1)
- update maven dependencies (757989c)
- update maven-plugin-api.version to v3.9.14 (bc68777)
- reduce vulnerable dependencies in sample modules (0876dc6)
- update examples to current project layout (f932c1c)
- update dependencies versions to fix security vulnerability (91031d0)
- harden @PolyglotClient convention binding (081e91e)
- harden Spring DX, fail-fast startup, and runtime contract (1e6f63f)
- scope mockito agent to runtime modules and fix strict docs link (ec142d3)
- update sonar project key (0067d45)


### 🧪 Internal
- align build script with Maven skipTests semantics (f34e13b)
- rename java-example to java-maven-example (db37a64)
- fix AbstractPolyglotExecutor tests after ScriptSource SPI (3b9fa7d)
- apply spotless formatting (8deb78c)
- normalize workflows (ci, security, release) (cad32ac)
- fix ref condition in release script (fbaeb0e)
- update github actions to v6 (b270e39)
- update dependency org.apache.maven.plugins:maven-enforcer-plugin to v3.6.2 (5eb2f1c)
- fix JaCoCo, surefire argLine and starter test setup (68683df)
- establish sonar new code baseline (fd4c283)
- add SonarCloud quality gate and coverage badges (9787b1d)
- add sonar maven plugin and project configuration (e0674b1)
- update dependency org.sonarsource.scanner.maven:sonar-maven-plugin to v5 (b640639)
- apply spotless formatting (16e1a1b)
- get language from filename by extension (60f9efd)
- add new stats_api_v2 example and update libraries_api (9f339bc)
- update actions/upload-artifact action to v7 (bdd4c2a)
- rename to polyglot-platform, update SCM, Sonar key, and README (84226a7)
- initial documentation setup (f6131cd)
- add Java+Python AOT example workflow (91831c3)
- update dependency maven to v3.9.14 (4ec5229)
- update github actions to v6 (478bef4)
- update dependency org.apache.maven.plugins:maven-surefire-plugin to v3.5.5 (3662d27)
- update dependency junit:junit to v3.8.2 (7ab3c71)
- fix workflows and improve CI pipeline (75aa3f1)
- update dependency com.diffplug.spotless:spotless-maven-plugin to v3.3.0 (83d0c72)
- update dependency org.apache.maven.plugins:maven-compiler-plugin to v3.15.0 (e3d9750)
- stabilize Maven CI and published Javadocs (dec49ce)
- update dependency org.codehaus.mojo:exec-maven-plugin to v3.6.3 (6edd083)
- update dependency numpy to v2.4.3 (10379ee)
- update dependency faker to v26.3.0 (d65ad3d)
- update dependency maven to v3.9.14 (8a39184)
- simplify .dev tasks for root aggregator (4c811c8)
- scope workflow triggers and harden security jobs (07d420f)
- fix invalid secret checks in analysis workflow (88a6fbe)
- fix quality profile spotbugs findings (7f0d06e)
- consolidate checks and streamline workflow layout (2978cb9)
- improve polyglot-adapter coverage and Sonar analysis (261dc84)
- run sonar once for monorepo (53d4742)
- enforce 70% coverage and publish sonar summary (5e8c84b)
- add preload and startup stabilization coverage (11bc48b)
- raise Sonar coverage with targeted API and starter tests (97299ff)
- harden workflow permissions and pin third-party actions (75659a5)
- refresh root readme with badges and project overview (7fac086)
- make Maven Central publishing manual-only (801b62f)
- make local release task prepare/tag only (e54cfe3)
- allow release script to resume prepared releases (f097c2d)


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

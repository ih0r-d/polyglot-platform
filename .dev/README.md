# Dev Automation

## Layout

- `bin/`: implementation scripts for local automation
- `lib/`: shared shell helpers for the scripts

The public Task interface lives in the repository root [Taskfile.yaml](../Taskfile.yaml).
`.dev/` is the implementation toolkit behind those commands.

## Usage

Use root-level Task commands as the canonical interface:

```bash
task dev:setup
```

Contributor commands:

- `task dev:setup`
- `task dev:hooks`
- `task build`
- `task test`
- `task verify`
- `task quality`
- `task format`

Maintainer and release commands:

- `task release:preflight`
- `task release:preflight:clean`
- `task release:dry-run`
- `task release -- 1.2.3`
- `task version`
- `task version:bump -- patch`
- `task git:clean-remote-tags`

For repository work, prefer the pinned SDKMAN environment first:

```bash
sdk env
```

First-time contributor setup:

```bash
task dev:setup
```

`task dev:setup` enables repository-local Git hooks for the current clone, runs a lightweight
local validation, does not publish artifacts, and does not require Maven Central or GPG secrets.
Hooks are not enabled automatically after clone.

Hook-only setup:

```bash
task dev:hooks
```

Common contributor commands:

```bash
task verify
task quality
task format
```

Advanced/internal usage:

Use scripts in `.dev/bin/` directly only when working on the toolkit itself or when you need to
debug the implementation behind a Task command.

## Rules

- Put real automation logic in `bin/*` scripts.
- Keep shared shell helpers in `lib/*`.
- Keep the root `Taskfile.yaml` as the only public Task interface.
- Run Maven through `./mvnw`.
- Developer automation scripts require `bash`.

## Requirements

- `java`
- `git`
- `bash`
- `./mvnw`
- `git cliff` for releases
- `python3` for docs preflight automation

## Safety

- `task dev:setup` enables repository-local Git hooks for the current clone, checks the local Maven/Java environment, and runs a lightweight validation step.
- `task dev:hooks` enables repository-local Git hooks for the current clone; it is not automatic after clone.
- `task verify` runs the standard repository verification path.
- `task quality` runs the stricter local quality gate.
- `task release:preflight`, `task release`, and `task release:dry-run` are maintainer-oriented release commands.
- `task release:preflight` runs quality verification, strict docs build, local artifact install, and
  maintained sample verification.
- `task release:preflight` creates `.venv-docs/` automatically when `mkdocs` is not available and
  reuses it on later runs.
- `task release:preflight:clean` removes `.venv-docs/` after the run; the default preflight keeps it for
  faster reruns.
- `task release` expects a clean working tree.
- `task release` runs the release preflight first, then updates project versions, regenerates
  `CHANGELOG.md`, commits, creates `v<version>`, and pushes `main` plus the tag.
- `task release` does not publish to Maven Central. Maven Central publishing is manual-only through `.github/workflows/publish-maven-central.yaml`.
- `task release:dry-run` verifies flattened POM generation and local deploy output for publishable modules only.
- `./.dev/dry-run-release.sh --log <path>` keeps terminal output and writes the full run log to the given file.
- `task git:clean-remote-tags` deletes remote tags that do not exist locally.

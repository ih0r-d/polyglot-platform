# Dev Automation

## Layout

- `bin/`: local developer entrypoints
- `lib/`: shared shell helpers
- `Taskfile.yaml`: thin wrapper around `bin/*`

## Usage

Use the scripts directly for local automation:

- `./.dev/bin/build.sh`
- `./.dev/bin/build.sh --module <module> --skip-tests`
- `./.dev/bin/clean.sh`
- `./.dev/bin/test.sh`
- `./.dev/bin/verify.sh`
- `./.dev/bin/format.sh`
- `./.dev/bin/version.sh`
- `./.dev/bin/bump.sh patch|minor|major`
- `./.dev/bin/release.sh <version>`
- `./.dev/dry-run-release.sh`
- `./.dev/dry-run-release.sh --log /tmp/polyglot-dry-run.log`
- `./.dev/bin/clean-remote-tags.sh`
- `./.dev/bin/pre-commit-maven.sh`

Use Task only as a convenience alias layer:

- `task -t .dev/Taskfile.yaml build`
- `task -t .dev/Taskfile.yaml bump -- minor`
- `task -t .dev/Taskfile.yaml dry-run-release`
- `task -t .dev/Taskfile.yaml dry-run-release -- --log /tmp/polyglot-dry-run.log`
- `task -t .dev/Taskfile.yaml release -- 1.2.3`
- `task -t .dev/Taskfile.yaml bump -- patch`

## Rules

- Put real automation logic in `bin/*` scripts.
- Keep shared shell helpers in `lib/*`.
- Keep `Taskfile.yaml` minimal and readable.
- Run Maven through `./mvnw`.

## Requirements

- `java`
- `git`
- `./mvnw`
- `git cliff` for releases

For repository work, prefer the pinned SDKMAN environment first:

```bash
sdk env
```

## Safety

- `release.sh` expects a clean working tree.
- `release.sh` updates project versions, regenerates `CHANGELOG.md`, commits, creates `v<version>`, and pushes `main` plus the tag.
- `release.sh` does not publish to Maven Central. Maven Central publishing is manual-only through `.github/workflows/publish-maven-central.yaml`.
- `dry-run-release.sh` verifies flattened POM generation and local deploy output for publishable modules only.
- `dry-run-release.sh --log <path>` keeps terminal output and writes the full run log to the given file.
- `clean-remote-tags.sh` deletes remote tags that do not exist locally.

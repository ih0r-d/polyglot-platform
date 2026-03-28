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
- `./.dev/bin/clean-remote-tags.sh`
- `./.dev/bin/pre-commit-maven.sh`

Use Task only as a convenience alias layer:

- `task -t .dev/Taskfile.yaml build`
- `task -t .dev/Taskfile.yaml bump -- minor`
- `task -t .dev/Taskfile.yaml release -- 1.2.3`

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

## Safety

- `release.sh` expects a clean working tree.
- `clean-remote-tags.sh` deletes remote tags that do not exist locally.

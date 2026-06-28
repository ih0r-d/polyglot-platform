# Release Process

This project should publish releases as repeatable, reviewable changes rather than ad hoc tag pushes.

Use this page together with:

- [`release-checklist.md`](release-checklist.md) for the operator checklist
- [`release-gates.md`](release-gates.md) for ship / no-ship criteria
- [`public-api.md`](public-api.md) and [`stability.md`](stability.md) when evaluating compatibility
  and experimental scope

## Release Checklist

1. Ensure `main` is green in CI.
2. Run `task release:preflight`.
3. Review dependency, security, and CodeQL workflow results.
4. Update `CHANGELOG.md` and any release notes if the preflight surfaced missing scope notes.
5. Confirm documentation and samples match the released API.
6. Run `task release -- <version>` to set the release version, update `CHANGELOG.md`, commit, tag, and push.
7. Run `task version:bump -- patch` after the release if you want to move `main` to the next patch snapshot.
8. Push the snapshot bump commit if you created one locally.
9. Run the `Publish Maven Central` workflow manually with the exact release tag after the tag is in GitHub.

## Release Preflight

`release-preflight` is the required local gate before `release`.

It runs, in order:

1. `./mvnw -B -ntp -Pquality verify`
2. strict documentation build
3. `./mvnw -B -ntp -DskipTests install`
4. maintained sample verification for:
   - `samples/java-maven-example`
   - `samples/java-maven-codegen-example`
   - `samples/spring-boot-example`

Use:

- `task release:preflight`
- `task release:preflight:clean`

`task release:preflight:clean` removes the local docs virtual environment after the run. The default
preflight keeps `.venv-docs/` to make repeated runs faster.

## Release Automation

- Pushing a tag that matches `v*` runs `.github/workflows/release.yaml`.
- That workflow verifies the tag matches the Maven project release version, runs the quality
  verification profile, runs a non-publishing Maven `release` profile dry run with
  `-Dgpg.skip=true -DskipTests`, and only then creates or updates the GitHub Release.
- Maven Central publishing is intentionally not part of the tag-push workflow.
- Publishing to Maven Central runs only from `.github/workflows/publish.yaml`, triggered with `workflow_dispatch`.
- The manual publish workflow checks out the requested tag, verifies it matches the Maven project version, runs the same non-publishing `release` profile dry run, and only then deploys with the existing Maven `release` profile and signing configuration.
- Local `.dev/bin/release.sh` no longer runs Maven `deploy`; it first runs the release preflight and
  then prepares and pushes the Git commit and tag for the release.

## Release Expectations

- Every release should have a changelog entry.
- Public API and compatibility-impacting changes must be described explicitly.
- Published artifacts should include source and javadoc jars where applicable.
- Release metadata must remain suitable for Maven Central publication.

## Branching

- `main` is the default integration branch.
- `release/*` branches are reserved for stabilization and patch releases when needed.

## After Release

- Verify the GitHub Release was created or updated from the tag push workflow.
- Verify published artifacts in Maven Central after the manual publish workflow completes.
- Verify generated site and documentation references if they changed.
- Announce breaking or compatibility-sensitive changes clearly in release notes.

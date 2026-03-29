# Release Process

This project should publish releases as repeatable, reviewable changes rather than ad hoc tag pushes.

## Release Checklist

1. Ensure `main` is green in CI.
2. Run `./mvnw -B -ntp -Pquality verify`.
3. Review dependency, security, and CodeQL workflow results.
4. Update `CHANGELOG.md` and any release notes.
5. Confirm documentation and samples match the released API.
6. Push a `v*` tag to trigger the GitHub release workflow.
7. Run the `Publish Maven Central` workflow manually with that exact tag after the tag is in GitHub.

## Release Automation

- Pushing a tag that matches `v*` runs `.github/workflows/release.yaml`.
- That workflow verifies the tag matches the Maven project release version and creates or updates the GitHub Release.
- Maven Central publishing is intentionally not part of the tag-push workflow.
- Publishing to Maven Central runs only from `.github/workflows/publish-maven-central.yaml`, triggered with `workflow_dispatch`.
- The manual publish workflow checks out the requested tag, verifies it matches the Maven project version, and deploys with the existing Maven `release` profile and signing configuration.

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

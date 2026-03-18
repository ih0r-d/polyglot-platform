# Release Process

This project should publish releases as repeatable, reviewable changes rather than ad hoc tag pushes.

## Release Checklist

1. Ensure `main` is green in CI.
2. Run `./mvnw -B -ntp -Pquality verify`.
3. Review dependency, security, and CodeQL workflow results.
4. Update `CHANGELOG.md` and any release notes.
5. Confirm documentation and samples match the released API.
6. Tag and publish using the maintained release automation in `.dev/`.

## Release Expectations

- Every release should have a changelog entry.
- Public API and compatibility-impacting changes must be described explicitly.
- Published artifacts should include source and javadoc jars where applicable.
- Release metadata must remain suitable for Maven Central publication.

## Branching

- `main` is the default integration branch.
- `release/*` branches are reserved for stabilization and patch releases when needed.

## After Release

- Verify published artifacts in the target repository.
- Verify generated site and documentation references if they changed.
- Announce breaking or compatibility-sensitive changes clearly in release notes.

# Releasing

`versions.properties` is the single place the version is written. The build reads it for the
`-SNAPSHOT` default and refuses a version whose first three components are not the Tabler
release `scripts/generate-tabler-icons.py` pins; on every push to `main` the `tag-releases`
workflow creates `v<version>` if it is not on origin yet and starts `publish`. Landing a bumped
entry on `main` is the release decision. Nobody types or pushes a tag.

```
3.46.0.0   Tabler 3.46.0, as first packaged here
3.46.0.1   the same icons with a change of ours (hand-written classes, a newer toolkit)
3.47.0.0   the next Tabler — VERSION + TARBALL_SHA256 in the generator, run it, commit, in one go
```

What `publish` does, in order: verifies the tag and the version against the generator's pin,
runs `check` (generates the resources from the pin, compiles, tests, Javadoc, and **regenerates
the enums from the pin and diffs** them against what is committed), **rehearses a consumer** (`scripts/rehearse-consumer.sh`: a
target-17 project resolves the pack from a file repository plus the toolkit from Central,
compiles against it and draws an icon), uploads the signed bundle, and drafts the GitHub
release. Nothing publishes itself: the deployment waits for **Publish** on the Central Portal.

## Bumping Tabler

1. Set `VERSION` and `TARBALL_SHA256` in the generator (download the tarball, hash it, and
   compare against what npm reports for the release).
2. Run `python3 scripts/generate-tabler-icons.py --only java`; review the enum diff (new
   constants, moved categories — a category change is a source-incompatible move for whoever
   named the old one; the last constant of an enum trading `;` for `,` is not a move). Without
   `--only java` it also writes the resources into `src/main/resources`, gitignored, and the
   build then fails in `processResources` on a duplicate `icons.blob`, because it generates its
   own copy under `build/` from the same pin; delete that directory if a full run left one.
3. Bump `versions.properties` to `<tabler>.0`, commit, push.
4. In limn-toolkit, bump `limn-icons-tabler` in the demo's catalog line, so the kitchen sink
   shows the new set.

## Bumping the toolkit

The pack compiles against `limn-toolkit` at the version in `gradle/libs.versions.toml` —
`compileOnly`, so that version is the oldest API the pack supports and reaches no consumer's
POM. Move it when the pack needs something newer (rare: two types, one call); spend the fourth
component.

## When something goes wrong

The same rules as the other repositories: a failed run after the tag exists — fix on `main`,
delete the tag on the web UI, push; a wrong staged deployment is Dropped for free; a wrong
published version stays and is superseded by the next number.

## Secrets

`MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD` (Portal user token), `SIGNING_KEY` (armored
private key), `SIGNING_PASSWORD` — the same four as every limn-toolkit repository.

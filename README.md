# limn-icons-tabler

The [Tabler Icons](https://tabler.io/icons) set as [Limn](https://github.com/limn-toolkit/limn-toolkit)
icons — one enum constant per icon, outline and filled, drawn through the toolkit's `SvgIcon` —
**versioned after Tabler rather than after the toolkit**: `3.46.0.x` is Tabler 3.46.0, a Limn
release re-uploads none of it, and your cache keeps it across toolkit upgrades.

```kotlin
dependencies {
    implementation("io.github.limn-toolkit:limn-icons-tabler:3.46.0.0")
}
```

```java
Icon save = TablerDevices.DEVICE_FLOPPY.icon();
new Button("Save").icon(save);
```

An application opts in; nothing in the toolkit depends on this pack, so an application that draws
a button never pulls six thousand icons onto its classpath. The pack is compiled against the
published `limn-toolkit` for two types (`Icon`, `SvgIcon`) and names no toolkit version of its
own: the toolkit your `limn-backend-lwjgl` line brings is the one it runs on.

## What is inside, and how it is made

Everything is the output of [`scripts/generate-tabler-icons.py`](scripts/generate-tabler-icons.py),
from an npm tarball pinned by version **and SHA-256** — and the two halves of that output are
treated differently on purpose:

- **The enums** (one per upstream category — a single enum of ~6000 constants exceeds a class
  initialiser's 64 KB) are **committed**: they are the public API, they are text, and a Tabler
  bump is a reviewable diff of added and moved names. `check` regenerates them from the pin and
  fails on any difference, so a hand edit or a forgotten run never reaches a release.
- **The resources** — the SVG blob, its index, Tabler's MIT licence — are **not committed**: 4 MB
  of upstream's drawings, reproducible byte for byte, generated into `build/` by the Gradle build
  (the first build downloads the tarball once and caches it). `TablerPackTest` asserts the
  committed enums and the generated blob describe the same set.

Edit the generator, not its output.

## Licences

The Java and the generator: Apache 2.0 (`LICENSE`). The drawings: MIT, Tabler's, in
`limn/icons/tabler/LICENSE.txt` inside the jar and in `NOTICE`. The POM names both.

## Releasing

`versions.properties` holds the one version (Tabler's three components plus one of ours). Bump
it — with `VERSION` and the checksum in the generator when Tabler moved, then run the generator
— push `main`, and the `tag-releases` workflow tags, checks, rehearses a consumer, uploads and
drafts. See [RELEASING.md](RELEASING.md).

rootProject.name = "limn-icons-tabler"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

// One project, one artifact: the Tabler icon set as Limn icons, released APART from the toolkit
// and versioned after Tabler (ADR 038 there). It used to be a module of limn-toolkit, re-published
// under the toolkit's version on every release although the drawings change only when Tabler
// does; here 3.46.0.x IS Tabler 3.46.0, and a toolkit release re-uploads none of it.
//
// It is COMPILED against the published limn-toolkit — the one thing the fonts and the FFmpeg
// payload do not need — for exactly two types, limn.graphics.Icon and limn.graphics.SvgIcon, and
// one call, SvgIcon.of; compileOnly, so its POM names no toolkit version and the toolkit's own
// repository depends on nothing here. Two repositories, one arrow, no cycle.

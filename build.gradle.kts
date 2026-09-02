// limn-icons-tabler: the Tabler icon set, as an opt-in Limn icon pack, versioned after Tabler.
//
// It implements a vocabulary the toolkit publishes (limn.graphics.Icon) and NOTHING in the
// toolkit depends on it, so an application that draws a button never pulls six thousand icons
// onto its classpath; the pack is a dependency an application chooses. It left the toolkit's
// repository (ADR 038 there) because its version belongs to Tabler, not to Limn: the drawings
// change when upstream releases, and re-publishing 4 MB of them under every toolkit version was
// a number that meant nothing and a download nobody needed.
//
// The sources under limn/icons/tabler are GENERATED, along with the resource blob, by
// scripts/generate-tabler-icons.py from a pinned upstream release. Edit the generator, not its
// output; CI regenerates from the pin and fails on any difference.

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import java.util.Properties

plugins {
    `java-library`
    alias(libs.plugins.central.publish)
}

group = "io.github.limn-toolkit"

// The version, from -PlimnIconsVersion (the publish workflow passes it, taken from the tag) and
// otherwise the -SNAPSHOT of versions.properties. Its first three components must be the Tabler
// release the generator pins: the number names what is inside, and the build refuses a lie.
val declaredVersion = Properties().apply {
    file("versions.properties").inputStream().use { load(it) }
}.getProperty("limn-icons-tabler") ?: throw GradleException("versions.properties names no version")
version = (findProperty("limnIconsVersion") as String?) ?: "$declaredVersion-SNAPSHOT"

val pinnedTabler = Regex("""^VERSION = "([^"]+)"""", RegexOption.MULTILINE)
    .find(file("scripts/generate-tabler-icons.py").readText())?.groupValues?.get(1)
    ?: throw GradleException("scripts/generate-tabler-icons.py no longer pins VERSION where this build reads it")
if (!version.toString().startsWith("$pinnedTabler.")) {
    throw GradleException(
        "version $version does not name the Tabler it carries: the generator pins $pinnedTabler, " +
                "so the version must be $pinnedTabler.<n> (see versions.properties)"
    )
}

java {
    // The JDK that RUNS the build, pinned for the same reason limn-toolkit pins it: the Javadoc
    // doclet differs between 17 and 21, and a published javadoc jar is an artifact like any other.
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    // What the artifact TARGETS: 17, the same promise limn-toolkit makes, and the honest minimum
    // for bytecode compiled against it. (The fonts and the FFmpeg payload say 8, because they are
    // resources; this is real code.)
    options.release.set(17)
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:all,-processing,-serial,-requires-automatic")
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        charSet = "UTF-8"
        addBooleanOption("html5", true)
        // The same doclint set as limn-toolkit: is the documentation that EXISTS correct and
        // resolvable; `missing` would demand `@param x the x` on generated enums.
        addBooleanOption("Xdoclint:all,-missing", true)
    }
}

tasks.named("check") {
    dependsOn(tasks.named("javadoc"))
}

dependencies {
    // The PUBLISHED toolkit, for limn.graphics.Icon and SvgIcon — compileOnly, on purpose. The
    // pack's POM names NO toolkit version: every application already has the toolkit through
    // limn-backend-lwjgl, and a transitive pin here would only ever be stale (compiled against
    // 0.5.0, running beside 0.6.0) and would tie the two release orders together. What this line
    // means is the OLDEST toolkit API the pack is compiled against; the toolkit repository, for
    // its part, does not depend on this pack in any way, so nothing forms a cycle.
    compileOnly(libs.limn.toolkit)
    // The tests need it for real (TablerWarmUpTest drives limn.concurrent.Ui).
    testImplementation(libs.limn.toolkit)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStackTraces = true
        showCauses = true
    }
}

// ------------------------------------------------------------------ what is generated when
//
// The enums are COMMITTED (they are the API: text, reviewable, a Tabler bump is a diff of names)
// and the resources are NOT (4 MB of upstream's drawings, reproducible byte for byte from a
// tarball pinned by version and SHA-256 — the same reasoning that keeps the FFmpeg payload out of
// its repository). So the build GENERATES the resources, into build/, every time the generator or
// its pin changes, and the tarball is cached under build/ so that only the first build needs the
// network. TablerPackTest then asserts the committed enums and the generated blob describe the
// same set, which is what catches a stale enum.
val generatorScript = layout.projectDirectory.file("scripts/generate-tabler-icons.py")
val generatedResources = layout.buildDirectory.dir("generated/tabler-resources")

val generateResources = tasks.register<Exec>("generateResources") {
    description = "Assembles icons.blob, icons.index and LICENSE.txt from the pinned Tabler release."
    group = "build"
    inputs.file(generatorScript)
    outputs.dir(generatedResources)
    commandLine("python3", generatorScript.asFile.absolutePath,
            "--only", "resources",
            "--resources-dir", generatedResources.get().dir("limn/icons/tabler").asFile.absolutePath,
            "--cache", layout.buildDirectory.dir("tabler-cache").get().asFile.absolutePath)
}

sourceSets {
    named("main") {
        resources.srcDir(generateResources)
    }
}

// The other half of the same review: the committed enums must be what the pin generates.
val verifyGenerated = tasks.register<Exec>("verifyGenerated") {
    description = "Regenerates the enums from the pinned Tabler release and fails if they differ."
    group = "verification"
    inputs.file(generatorScript)
    inputs.dir("src/main/java/limn/icons/tabler")
    commandLine("bash", "scripts/verify-generated.sh")
}
tasks.named("check") {
    dependsOn(verifyGenerated)
}

mavenPublishing {
    publishToMavenCentral()

    if (providers.gradleProperty("signingInMemoryKey").isPresent ||
            providers.gradleProperty("signing.keyId").isPresent) {
        signAllPublications()
    }

    pom {
        name.set("limn-icons-tabler")
        description.set(
            "The Tabler icon set (v$pinnedTabler) as Limn icons: one enum constant per icon, " +
                    "outline and filled, drawn through the toolkit's SvgIcon. An application opts " +
                    "in; nothing in the toolkit depends on it. Versions with Tabler, not with " +
                    "the toolkit."
        )
        url.set("https://github.com/limn-toolkit/limn-icons-tabler")
        scm {
            url.set("https://github.com/limn-toolkit/limn-icons-tabler")
            connection.set("scm:git:https://github.com/limn-toolkit/limn-icons-tabler.git")
            developerConnection.set("scm:git:ssh://git@github.com/limn-toolkit/limn-icons-tabler.git")
        }
        // Both, because the artifact is both: the enums, the loader and the generator are Limn's
        // own code under Apache 2.0; the drawings in the resource blob are Tabler's under MIT,
        // whose text travels in the jar beside them (limn/icons/tabler/LICENSE.txt).
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                comments.set("The Java classes and the generator")
            }
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                comments.set("The Tabler Icons drawings in the resource blob; see limn/icons/tabler/LICENSE.txt in the jar")
            }
        }
        developers {
            developer {
                id.set("dyorgio")
                name.set("Dyorgio Nascimento")
                url.set("https://github.com/dyorgio")
            }
        }
    }
}

publishing {
    // A plain file repository under build/repo, for looking at what would ship without sending
    // it anywhere — and for scripts/rehearse-consumer.sh to resolve from.
    repositories {
        maven {
            name = "buildDir"
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}

// A release that Central would reject on validation (unsigned), caught before anything leaves.
gradle.taskGraph.whenReady {
    val releasing = allTasks.any {
        it.name == "publishToMavenCentral" || it.name == "publishAndReleaseToMavenCentral" ||
                it.name.startsWith("publishAllPublicationsToMavenCentral")
    }
    if (!releasing) return@whenReady
    if (project.version.toString().endsWith("-SNAPSHOT")) {
        logger.lifecycle("publishing ${project.version} to Central's SNAPSHOT repository. This is " +
                "not a release: bump versions.properties and push for one (see RELEASING.md).")
        return@whenReady
    }
    if (!providers.gradleProperty("signingInMemoryKey").isPresent &&
            !providers.gradleProperty("signing.keyId").isPresent) {
        throw GradleException(
            "refusing to publish ${project.version} to Maven Central unsigned: no signing key is " +
                    "configured, and Central requires a signature on every artifact of a release."
        )
    }
}

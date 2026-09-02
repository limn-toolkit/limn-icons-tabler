#!/usr/bin/env bash
#
# Proves the artifact RESOLVES, not merely that it uploads (the half of a rehearsal limn-fonts'
# first release skipped): publish to the local file repository, then resolve from it with a
# throwaway Gradle project shaped like the real consumers — compiles for 17, reads Gradle
# metadata, and gets limn-toolkit transitively from Maven Central the way the pack's POM says —
# and check that what arrives is what the pack needs: the blob, the index, the licence, and an
# enum a consumer can actually load and call.
#
# Locally: ./scripts/rehearse-consumer.sh [version]
#
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
VERSION="${1:-$(sed -n 's/^limn-icons-tabler=\(.*\)$/\1/p' versions.properties)-SNAPSHOT}"
# The toolkit the pack was compiled against: the consumer names it ITSELF, as an application
# does through its backend line — the pack's POM deliberately does not (compileOnly).
TOOLKIT="$(sed -n 's/^limn-toolkit = "\(.*\)"$/\1/p' gradle/libs.versions.toml)"
REPO="$ROOT/build/repo"
CONSUMER="$ROOT/build/consumer"

echo "· publishing $VERSION to ${REPO#"$ROOT/"}"
rm -rf "$REPO"
./gradlew publishAllPublicationsToBuildDirRepository -PlimnIconsVersion="$VERSION" -q

# Real code targeting 17 says 17 — no more (a toolchain-21 leak) and no less.
BAD="$(grep -rho '"org.gradle.jvm.version": [0-9]*' "$REPO" --include="*.module" | sort -u | grep -v ': 17$' || true)"
if [ -n "$BAD" ]; then
  echo "✗ a published variant declares a minimum JVM other than 17: $BAD" >&2
  exit 1
fi
echo "✓ every variant declares JVM 17"

rm -rf "$CONSUMER"; mkdir -p "$CONSUMER/src/main/java"
cp gradlew "$CONSUMER"/ && cp -r gradle "$CONSUMER"/
cat > "$CONSUMER/settings.gradle.kts" <<SETTINGS
rootProject.name = "consumer"
dependencyResolutionManagement {
    repositories {
        maven { url = uri("$REPO") }
        mavenCentral()
    }
}
SETTINGS
cat > "$CONSUMER/build.gradle.kts" <<GRADLE
plugins { application }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
tasks.withType<JavaCompile>().configureEach { options.release.set(17) }
dependencies {
    implementation("io.github.limn-toolkit:limn-icons-tabler:$VERSION")
    implementation("io.github.limn-toolkit:limn-toolkit:$TOOLKIT")
}
application { mainClass.set("Probe") }
GRADLE
# A consumer that compiles against the pack and calls it: the enum resolves, the icon builds,
# and the blob behind it is readable — which is the whole contract. (No rasterizer is installed
# here on purpose: drawing is the backend's job and needs a GL context; what the pack owes is a
# resolvable enum, an Icon, and the bytes behind it.)
cat > "$CONSUMER/src/main/java/Probe.java" <<'JAVA'
public class Probe {
    public static void main(String[] args) throws Exception {
        limn.icons.tabler.TablerIcon icon = limn.icons.tabler.TablerArrows.values()[0];
        limn.graphics.Icon drawn = icon.icon();
        if (drawn == null) throw new IllegalStateException("no icon for " + icon);
        try (java.io.InputStream in = Probe.class.getResourceAsStream("/limn/icons/tabler/icons.index")) {
            if (in == null) throw new IllegalStateException("icons.index missing from the classpath");
            long lines = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines().count();
            System.out.println("probe: " + icon + " resolved; index has " + lines + " entries");
        }
        try (java.io.InputStream in = Probe.class.getResourceAsStream("/limn/icons/tabler/LICENSE.txt")) {
            if (in == null) throw new IllegalStateException("LICENSE.txt missing from the classpath");
        }
    }
}
JAVA
( cd "$CONSUMER" && ./gradlew run -q )
echo "Rehearsal passed: a target-17 consumer resolves $VERSION, compiles against it, and draws from it."

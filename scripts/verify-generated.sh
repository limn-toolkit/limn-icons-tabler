#!/usr/bin/env bash
#
# The committed enums must be what the pinned Tabler release generates.
#
# The enums under src/main/java/limn/icons/tabler are the generator's output and the pack's public
# API: committed so that a Tabler bump is a reviewable diff of names, and therefore able to drift
# — a hand edit, a bump of VERSION without a run, a run without a commit. This regenerates them
# into a scratch directory from the pin (through the same tarball cache the build uses) and diffs;
# any difference fails. `check` runs it, so CI and the release do too. The resources are not
# compared here because they are not committed at all: the build generates them.
#
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SCRATCH="$(mktemp -d)"
trap 'rm -rf "$SCRATCH"' EXIT
python3 scripts/generate-tabler-icons.py --only java --java-dir "$SCRATCH" \
    --cache "$ROOT/build/tabler-cache" ${TABLER_PACKAGE_DIR:+--package "$TABLER_PACKAGE_DIR"} >/dev/null

# The hand-written classes live beside the generated ones and are not the generator's to write.
if ! diff -rq --exclude='Tabler.java' --exclude='TablerIcon.java' --exclude='package-info.java' \
      "$SCRATCH" "$ROOT/src/main/java/limn/icons/tabler" >/dev/null; then
  echo "✗ the committed enums differ from what the pinned Tabler release generates:" >&2
  diff -rq --exclude='Tabler.java' --exclude='TablerIcon.java' --exclude='package-info.java' \
      "$SCRATCH" "$ROOT/src/main/java/limn/icons/tabler" >&2 || true
  echo "  Run scripts/generate-tabler-icons.py and commit the result (or revert the hand edit)." >&2
  exit 1
fi
echo "✓ the committed enums are what the pinned Tabler release generates"

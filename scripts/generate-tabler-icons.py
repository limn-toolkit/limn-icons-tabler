#!/usr/bin/env python3
"""Generates limn-icons-tabler from a pinned upstream release.

Two outputs, treated differently on purpose:

  * the enums under src/main/java/limn/icons/tabler are COMMITTED: they are the pack's public
    API, they are text, and a Tabler bump becomes a reviewable diff of added and moved names.
    `check` regenerates them from the pin and fails on any difference (scripts/verify-generated.sh),
    so a hand edit or a forgotten run never reaches a release.
  * the resources — icons.blob, icons.index, LICENSE.txt — are NOT committed: 4 MB of upstream's
    drawings, reproducible byte for byte from the pinned tarball, generated into build/ by the
    Gradle build (task generateResources) the way the FFmpeg payload is built rather than stored.
    TablerPackTest asserts the committed enums and the generated blob describe the same set.

    python3 scripts/generate-tabler-icons.py --only java           # the enums, into src/ (a bump)
    python3 scripts/generate-tabler-icons.py                       # also the resources into src/,
                                                                   # which the build then meets twice
    python3 scripts/generate-tabler-icons.py --only java --java-dir DIR
    python3 scripts/generate-tabler-icons.py --only resources --resources-dir DIR
    python3 scripts/generate-tabler-icons.py [...] --cache DIR     # keep the tarball for offline reruns
    python3 scripts/generate-tabler-icons.py [...] --package DIR   # an already-extracted tarball

The tarball is verified against SHA256 below before a byte of it is read. Bumping the version
means bumping both, running this with no arguments, and running the tests.
"""

from __future__ import annotations

import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
import urllib.request

VERSION = "3.48.0"
TARBALL_URL = f"https://registry.npmjs.org/@tabler/icons/-/icons-{VERSION}.tgz"
TARBALL_SHA256 = "28447dcf6f0bb2b8d92c59a1b3d30900a180de2e97d7db4d267e6320dc68f449"

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODULE = ROOT  # this repository IS the module
JAVA_DIR = os.path.join(MODULE, "src", "main", "java", "limn", "icons", "tabler")
RES_DIR = os.path.join(MODULE, "src", "main", "resources", "limn", "icons", "tabler")

# Java keywords cannot be enum constants; no Tabler name currently collides, but a future
# release adding "new" or "class" would otherwise produce a module that does not compile.
JAVA_KEYWORDS = {
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
    "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
    "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
    "interface", "long", "native", "new", "package", "private", "protected", "public",
    "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
    "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false",
    "null", "_",
}


def fetch(cache: str | None) -> str:
    """Downloads (or reuses a cached copy of) the pinned tarball, verifies it, extracts it."""
    tmp = tempfile.mkdtemp(prefix="tabler-")
    if cache:
        os.makedirs(cache, exist_ok=True)
        archive = os.path.join(cache, f"icons-{VERSION}.tgz")
    else:
        archive = os.path.join(tmp, "icons.tgz")
    if not os.path.isfile(archive):
        print(f"fetching {TARBALL_URL}")
        try:
            urllib.request.urlretrieve(TARBALL_URL, archive + ".part")
        except OSError as error:
            raise SystemExit(f"cannot fetch the pinned Tabler tarball ({error}); "
                             "a build needs the network once, then --cache serves it") from error
        os.replace(archive + ".part", archive)
    # Verified on EVERY use, cached or not: the cache is a convenience, the digest is the pin.
    digest = hashlib.sha256(open(archive, "rb").read()).hexdigest()
    if digest != TARBALL_SHA256:
        os.remove(archive)
        raise SystemExit(f"checksum mismatch\n  expected {TARBALL_SHA256}\n  got      {digest}")
    with tarfile.open(archive) as tar:
        # The "data" filter refuses path traversal and the like; it exists from 3.9.17/3.12 and
        # macOS still ships 3.9.6, where the keyword is unknown. The archive is pinned by digest
        # either way, which is the stronger guarantee; the filter is defence in depth where it
        # is available.
        try:
            tar.extractall(tmp, filter="data")
        except TypeError:
            tar.extractall(tmp)
    return os.path.join(tmp, "package")


def constant(name: str) -> str:
    text = re.sub(r"[^A-Za-z0-9]+", "_", name).upper().strip("_")
    if not text or text[0].isdigit():
        text = "I_" + text
    if text.lower() in JAVA_KEYWORDS:
        text += "_"
    return text


def type_name(category: str) -> str:
    words = re.sub(r"[^A-Za-z0-9]+", " ", category or "Uncategorised").split()
    return "Tabler" + "".join(word[:1].upper() + word[1:] for word in words)


def parse_args(argv: list[str]) -> dict:
    options = {"only": "all", "java_dir": JAVA_DIR, "res_dir": RES_DIR, "cache": None, "package": None}
    it = iter(argv)
    for arg in it:
        if arg == "--only":
            options["only"] = next(it)
        elif arg == "--java-dir":
            options["java_dir"] = os.path.abspath(next(it))
        elif arg == "--resources-dir":
            options["res_dir"] = os.path.abspath(next(it))
        elif arg == "--cache":
            options["cache"] = os.path.abspath(next(it))
        elif arg == "--package":
            options["package"] = os.path.abspath(next(it))
        elif not arg.startswith("-") and options["package"] is None:
            options["package"] = os.path.abspath(arg)  # the old positional form
        else:
            raise SystemExit(f"unknown argument {arg}; see the docstring")
    if options["only"] not in ("all", "java", "resources"):
        raise SystemExit("--only takes all, java or resources")
    return options


def main() -> None:
    options = parse_args(sys.argv[1:])
    package = options["package"] or fetch(options["cache"])
    manifest = json.load(open(os.path.join(package, "icons.json")))
    JAVA_DIR = options["java_dir"]  # noqa: N806 - shadows the module defaults on purpose
    RES_DIR = options["res_dir"]  # noqa: N806
    want_java = options["only"] in ("all", "java")
    want_resources = options["only"] in ("all", "resources")

    # ---------------------------------------------------------------- resources
    # The index is needed for the enums too (which names have a filled variant), so the blob is
    # always assembled; whether it is WRITTEN is the caller's choice.
    os.makedirs(RES_DIR, exist_ok=True) if want_resources else None
    blob = bytearray()
    index = []
    for style in ("outline", "filled"):
        directory = os.path.join(package, "icons", style)
        for file in sorted(os.listdir(directory)):
            if not file.endswith(".svg"):
                continue
            data = open(os.path.join(directory, file), "rb").read()
            index.append(f"{style}/{file[:-4]}\t{len(blob)}\t{len(data)}")
            blob += data

    if want_resources:
        with open(os.path.join(RES_DIR, "icons.blob"), "wb") as out:
            out.write(blob)
        with open(os.path.join(RES_DIR, "icons.index"), "w", encoding="utf-8") as out:
            out.write("\n".join(index) + "\n")
        shutil.copyfile(os.path.join(package, "LICENSE"), os.path.join(RES_DIR, "LICENSE.txt"))
    if not want_java:
        print(f"blob {len(blob):,} bytes · {len(index):,} entries → {RES_DIR}")
        return

    # ---------------------------------------------------------------- enums
    by_category: dict[str, list[str]] = {}
    for name in sorted(manifest):
        by_category.setdefault(manifest[name].get("category") or "", []).append(name)

    for stale in os.listdir(JAVA_DIR) if os.path.isdir(JAVA_DIR) else []:
        if stale.startswith("Tabler") and stale not in ("TablerIcon.java", "Tabler.java"):
            os.remove(os.path.join(JAVA_DIR, stale))
    os.makedirs(JAVA_DIR, exist_ok=True)

    filled = {line.split("\t")[0][len("filled/"):] for line in index if line.startswith("filled/")}
    types = []
    for category, names in sorted(by_category.items()):
        java_type = type_name(category)
        types.append((java_type, category or "Uncategorised", len(names)))
        used: set[str] = set()
        constants = []
        for name in names:
            text = constant(name)
            while text in used:
                text += "_"
            used.add(text)
            constants.append(f'    {text}("{name}")')
        body = ",\n".join(constants)
        label = category or "no category upstream"
        with open(os.path.join(JAVA_DIR, java_type + ".java"), "w", encoding="utf-8") as out:
            out.write(f'''package limn.icons.tabler;

/**
 * Tabler's <b>{label}</b> icons, one constant per name.
 *
 * <p>Generated. The set is split across one enum per upstream category because a single
 * enum cannot hold it: a class initialiser is capped at 64KB of bytecode and every constant
 * costs roughly twenty of them, so one enum holding every Tabler name does not compile at
 * all. The categories are upstream's own, and the largest of them is comfortably inside the
 * ceiling.
 */
public enum {java_type} implements TablerIcon {{

{body};

    private final String iconName;

    {java_type}(String iconName) {{
        this.iconName = iconName;
    }}

    @Override
    public String iconName() {{
        return iconName;
    }}
}}
''')

    total = sum(count for _, _, count in types)
    print(f"blob {len(blob):,} bytes · {len(index):,} entries · "
          f"{len(types)} enums · {total:,} constants")
    for java_type, label, count in sorted(types, key=lambda t: -t[2])[:5]:
        print(f"  {java_type:<28} {label:<20} {count}")
    print(f"  filled variants available: {len(filled):,}")


if __name__ == "__main__":
    main()

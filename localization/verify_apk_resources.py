#!/usr/bin/env python3
"""Compare compiled Alpha/Mock string resources with every source catalog using AAPT2."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import tempfile
import validate_catalog as validator
import android_resources as converter


def parse_dump(text):
    table = {}
    key = None
    config = None
    parts = []

    def finish():
        nonlocal config, parts
        if config is not None:
            value = "\n".join(parts)
            if not value.endswith('"'):
                raise ValueError(f"unterminated compiled string: {key}/{config}")
            entries = table.setdefault(key, {})
            if config in entries:
                raise ValueError(f"duplicate compiled string: {key}/{config}")
            entries[config] = value[:-1]
        config, parts = None, []

    for line in text.removesuffix("\n").split("\n"):
        if line.startswith("    resource ") or line.startswith("  type "):
            finish()
            match = re.match(r"    resource \S+ string/(\w+)$", line)
            key = match.group(1) if match else None
        elif key is not None:
            match = re.match(r'      \(([^)]*)\) "(.*)$', line)
            if match:
                finish()
                config = match.group(1)
                parts = [match.group(2)]
            elif config is not None and line.startswith("      "):
                parts.append(line[6:])
            elif config is not None and line == "":
                parts.append("")
    finish()
    return table


def verify(table, source, catalogs, brand):
    if table.keys() != source.keys():
        raise ValueError("compiled string keys differ from source")
    count = 0
    for key, original in source.items():
        expected = {"": brand if key == "app_name" else original}
        if key not in converter.EXCLUDED:
            expected.update({tag: catalogs[tag][key] for tag in validator.LANGUAGES})
        if table[key] != expected:
            mismatches = sorted(set(table[key]) | set(expected))
            mismatches = [tag or "default" for tag in mismatches if table[key].get(tag) != expected.get(tag)]
            raise ValueError(f"compiled resource mismatch: {key}/{','.join(mismatches)}")
        count += len(expected)
    return count


def verify_literal_encoding(aapt2, android_jar):
    values = {"edge": '  A\n\nB\tC\\D"E\'F  ', "reference": "@string/other",
              "theme_reference": "?attr/other", "markup": "A & <B>",
              "arguments": "%1$s / %2$02d", "carriage": "A\rB"}
    with tempfile.TemporaryDirectory(prefix="hoyi-literal-") as temporary:
        root = Path(temporary)
        directory = root / "res/values"
        directory.mkdir(parents=True)
        (directory / "strings.xml").write_bytes(converter.render(values))
        manifest = root / "AndroidManifest.xml"
        manifest.write_text('<manifest package="io.openhoyi.localetests" xmlns:android="http://schemas.android.com/apk/res/android"><application android:label="Fixture" /></manifest>')
        compiled, apk = root / "compiled.zip", root / "fixture.apk"
        subprocess.run([str(aapt2), "compile", "--dir", str(root / "res"), "-o", str(compiled)], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        subprocess.run([str(aapt2), "link", "-I", str(android_jar), "--manifest", str(manifest), "-o", str(apk), str(compiled)], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        dump = subprocess.run([str(aapt2), "dump", "resources", str(apk)], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout.decode("utf-8")
        if parse_dump(dump) != {key: {"": value} for key, value in values.items()}:
            raise ValueError("compiled literal encoding differs from input")
    return len(values)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--aapt2", type=Path, required=True)
    parser.add_argument("--android-jar", type=Path, required=True)
    parser.add_argument("--alpha", type=Path, required=True)
    parser.add_argument("--mock", type=Path, required=True)
    args = parser.parse_args()
    directory = validator.ROOT / "localization/catalog"
    try:
        if not validator.report(directory)["complete"]:
            raise ValueError("translation catalogs are invalid")
        source = validator.source_catalog(directory / "source.json")
        catalogs = {tag: validator.decode_json((directory / f"{tag}.json").read_text()) for tag in validator.LANGUAGES}
        result = {"literalRoundTrips": verify_literal_encoding(args.aapt2, args.android_jar)}
        for name, apk, brand in [("alpha", args.alpha, "OpenHOYI Alpha"), ("mock", args.mock, "HOYI Mock")]:
            dump = subprocess.run([str(args.aapt2), "dump", "resources", str(apk)], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout.decode("utf-8")
            result[name] = {"matchedStrings": verify(parse_dump(dump), source, catalogs, brand)}
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        print(json.dumps({"error": str(error)}, ensure_ascii=False))
        return 1
    print(json.dumps(result, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

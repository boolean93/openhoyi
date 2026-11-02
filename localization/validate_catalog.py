#!/usr/bin/env python3
"""Validate staged translations. Reports gaps; never writes or enables Android resources."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

LANGUAGES = ("en", "ru", "th", "ar", "ja", "ko", "es")
FORMAT = re.compile(r"%[1-9][0-9]*\$(?:[0-9]+)?[sd]")
CONTROL = re.compile(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f\u061c\u200e\u200f\u202a-\u202e\u2066-\u2069]")
ROOT = Path(__file__).resolve().parent.parent


def decode_json(text):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f"duplicate key: {key}")
            result[key] = value
        return result
    return json.loads(text, object_pairs_hook=unique)


def arguments(text):
    tokens = FORMAT.findall(text) + ["%%"] * text.count("%%")
    if "%" in FORMAT.sub("", text).replace("%%", ""):
        raise ValueError("unknown format")
    return Counter(tokens)


def validate(source, translated):
    if not isinstance(translated, dict):
        return ["catalog: type"]
    errors = [f"{key}: missing" for key in sorted(source.keys() - translated.keys())]
    errors += [f"{key}: unexpected" for key in sorted(translated.keys() - source.keys())]
    for key in sorted(source.keys() & translated.keys()):
        value = translated[key]
        if not isinstance(value, str):
            errors.append(f"{key}: type")
            continue
        if not value.strip():
            errors.append(f"{key}: empty")
        if CONTROL.search(value):
            errors.append(f"{key}: control")
        try:
            if arguments(source[key]) != arguments(value):
                errors.append(f"{key}: format")
        except ValueError:
            errors.append(f"{key}: format")
        if value.count("\n") != source[key].count("\n"):
            errors.append(f"{key}: newlines")
    return errors


def source_catalog(path):
    document = decode_json(path.read_text())
    if not isinstance(document, dict):
        raise ValueError("source snapshot must be an object")
    xml_path = ROOT / "mobile/src/main/res/values/strings.xml"
    if document.get("source") != "mobile/src/main/res/values/strings.xml":
        raise ValueError("unexpected source resource path")
    if document.get("sha256") != hashlib.sha256(xml_path.read_bytes()).hexdigest():
        raise ValueError("source snapshot is stale")
    actual = {}
    for node in ET.parse(xml_path).getroot().findall("string"):
        key = node.attrib["name"]
        if key in actual:
            raise ValueError(f"duplicate source resource: {key}")
        value = "".join(node.itertext())
        if value.startswith('"') and value.endswith('"'):
            value = value[1:-1]
        actual[key] = value.replace("\\n", "\n")
    if document.get("strings") != actual:
        raise ValueError("source snapshot content differs from resources")
    return actual


def report(directory):
    source = source_catalog(directory / "source.json")
    result = {"sourceKeys": len(source), "languages": {}}
    for tag in LANGUAGES:
        path = directory / f"{tag}.json"
        try:
            translated = decode_json(path.read_text())
            errors = validate(source, translated)
            count = len(source.keys() & translated.keys()) if isinstance(translated, dict) else 0
        except (OSError, ValueError) as error:
            translated = {}
            errors = [f"catalog: {type(error).__name__}"]
            count = 0
        result["languages"][tag] = {"present": count, "errors": errors, "valid": not errors}
    result["complete"] = all(item["valid"] for item in result["languages"].values())
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path, default=ROOT / "localization/catalog")
    args = parser.parse_args()
    try:
        result = report(args.directory)
    except (OSError, ValueError) as error:
        print(json.dumps({"complete": False, "sourceError": str(error)}, ensure_ascii=False))
        return 2
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if result["complete"] else 1


if __name__ == "__main__":
    raise SystemExit(main())

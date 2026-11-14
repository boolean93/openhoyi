"""Deterministic, source-bound photo import prompt. No external AI or device access."""
import argparse
import hashlib
import json
from pathlib import Path
import re

ASSET_NAME = "OpenHOYI-curve-import-prompt.zh-CN.txt"
MAX_BYTES = 65536
DIRECTORY = "docs/curve-import"
# Deliberate review gate: changes require reviewing the prose AND actual JVM tests.
CONTRACT_FILES = (
    "mobile/src/main/kotlin/io/openhoyi/mobile/CustomCurveDocument.kt",
    "mobile/src/main/kotlin/io/openhoyi/mobile/CustomCurveAdapter.kt",
    "mobile/src/main/kotlin/io/openhoyi/mobile/CustomPressureCurveAdapter.kt",
    "mobile/src/main/kotlin/io/openhoyi/mobile/CurveDocumentPresentation.kt",
    "protocol-core/src/main/kotlin/io/openhoyi/protocol/CustomCurveStartPolicy.kt",
    DIRECTORY + "/photo-to-json.zh-CN.txt",
    DIRECTORY + "/examples/pressure.json",
    DIRECTORY + "/examples/flow.json",
)

def digest(data):
    return hashlib.sha256(data).hexdigest()

def source_bytes(root, name):
    root = Path(root).resolve()
    path = root / name
    if path.is_symlink() or not path.resolve().is_relative_to(root):
        raise ValueError("Prompt input escapes source root")
    data = path.read_bytes()
    if not data or len(data) > 1024 * 1024:
        raise ValueError("Invalid prompt input size")
    return data

def verify_source(source_root):
    root = Path(source_root)
    if not (root / DIRECTORY).exists():
        return None  # Historical source, not a partially populated new contract.
    contract = json.loads(source_bytes(root, DIRECTORY + "/contract.json"))
    if (set(contract) != {"schema", "revision", "files"} or type(contract["schema"]) is not int
            or contract["schema"] != 3 or type(contract["revision"]) is not int or contract["revision"] < 1
            or not isinstance(contract["files"], dict) or set(contract["files"]) != set(CONTRACT_FILES)):
        raise ValueError("Invalid prompt contract")
    for name in CONTRACT_FILES:
        if digest(source_bytes(root, name)) != contract["files"][name]:
            raise ValueError("Prompt contract drift: review text, examples and JVM checks: " + name)
    return contract

def validate_metadata(meta):
    if (not isinstance(meta, dict) or set(meta) != {"file", "sha256", "schema", "revision"}
            or meta["file"] != ASSET_NAME or not isinstance(meta["sha256"], str)
            or re.fullmatch(r"[0-9a-f]{64}", meta["sha256"]) is None
            or type(meta["schema"]) is not int or meta["schema"] != 3
            or type(meta["revision"]) is not int or meta["revision"] < 1):
        raise ValueError("Invalid curve import prompt metadata")
    return meta

def build_prompt(source_root, output_dir, manifest):
    contract = verify_source(source_root)
    if contract is None:
        return None
    code, version, source = (manifest.get(k) for k in ("versionCode", "versionName", "declaredSourceCommit"))
    if (type(code) is not int or code < 1 or not isinstance(version, str)
            or not re.fullmatch(r"[A-Za-z0-9.+-]{1,100}", version)
            or not isinstance(source, str) or not re.fullmatch(r"[0-9a-f]{40}", source)):
        raise ValueError("Invalid prompt release identity")
    text = (f"OpenHOYI 官方参数截图导入提示词\n适用版本：{version}（versionCode {code}）\n"
            f"导入schema：3；提示词修订：{contract['revision']}\n源码：{source}\n"
            "请将本文件全文和完整截图一起提供给支持图片的 AI；链接无法读取时请上传TXT或粘贴全文。\n\n")
    text += source_bytes(source_root, DIRECTORY + "/photo-to-json.zh-CN.txt").decode("utf-8").strip() + "\n"
    for name, title in (("pressure", "结构示例：变压恒压"), ("flow", "结构示例：变流快速、首段流量")):
        raw = source_bytes(source_root, DIRECTORY + f"/examples/{name}.json").decode("utf-8")
        # The real parser/adapter tests own semantics. This check catches malformed build inputs.
        if not isinstance(json.loads(raw), dict):
            raise ValueError("Prompt example is not an object")
        text += "\n" + title + "（仅示例，不是待搬运配方）：\n" + raw.strip() + "\n"
    data = text.encode("utf-8")
    if len(data) > MAX_BYTES:
        raise ValueError("Prompt exceeds size limit")
    output = Path(output_dir)
    output.mkdir(parents=True, exist_ok=True)
    (output / ASSET_NAME).write_bytes(data)
    return validate_metadata(dict(file=ASSET_NAME, sha256=digest(data), schema=3, revision=contract["revision"]))

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("check", "lock"))
    parser.add_argument("--source", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    if args.action == "lock":
        path = args.source / DIRECTORY / "contract.json"
        old = json.loads(path.read_text()) if path.exists() else {"revision": 1}
        contract = dict(schema=3, revision=old["revision"], files={name: digest(source_bytes(args.source, name)) for name in CONTRACT_FILES})
        path.write_text(json.dumps(contract, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if verify_source(args.source) is None:
        raise ValueError("Current source has no prompt contract")
    print("Curve import prompt source contract verified")

if __name__ == "__main__":
    main()

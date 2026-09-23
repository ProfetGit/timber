#!/usr/bin/env python3
"""Validate the pack and build dist/Timber-<version>.zip (deterministic)."""
import json
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PACK = ROOT / "pack"
DIST = ROOT / "dist"
NAMESPACE = "timber"


def version() -> str:
    load = (PACK / "data/timber/function/load.mcfunction").read_text()
    m = re.search(r'storage timber:meta version set value "([^"]+)"', load)
    if not m:
        sys.exit("version string not found in load.mcfunction")
    return m.group(1)


def check_json(errors: list[str]) -> None:
    for f in PACK.rglob("*"):
        if f.suffix in (".json", ".mcmeta"):
            try:
                json.loads(f.read_text())
            except json.JSONDecodeError as e:
                errors.append(f"{f.relative_to(ROOT)}: {e}")


def resource_roots() -> list[Path]:
    roots = [PACK]
    meta = json.loads((PACK / "pack.mcmeta").read_text())
    for entry in meta.get("overlays", {}).get("entries", []):
        roots.append(PACK / entry["directory"])
    return roots


def check_refs(errors: list[str]) -> None:
    roots = resource_roots()

    def exists(kind: str, rid: str, ext: str) -> bool:
        ns, path = rid.split(":", 1) if ":" in rid else ("minecraft", rid)
        return any((r / "data" / ns / kind / f"{path}{ext}").exists() for r in roots)

    ref = re.compile(r"\bfunction (#?)([a-z0-9_.-]+:[a-z0-9_./-]+)")
    pred = re.compile(r"\bpredicate ([a-z0-9_.-]+:[a-z0-9_./-]+)")
    tag = re.compile(r"#(timber:[a-z0-9_./-]+)")
    for root in roots:
        for f in (root / "data").rglob("*.mcfunction"):
            text = f.read_text()
            rel = f.relative_to(ROOT)
            for n, line in enumerate(text.splitlines(), 1):
                if line.lstrip().startswith("#"):
                    continue
                for is_tag, rid in ref.findall(line):
                    if not rid.startswith(NAMESPACE + ":"):
                        continue
                    kind = "tags/function" if is_tag else "function"
                    ext = ".json" if is_tag else ".mcfunction"
                    if not exists(kind, rid, ext):
                        errors.append(f"{rel}:{n}: missing function {'#' if is_tag else ''}{rid}")
                for rid in pred.findall(line):
                    if not exists("predicate", rid, ".json"):
                        errors.append(f"{rel}:{n}: missing predicate {rid}")
                for rid in tag.findall(line):
                    if "$(" in line and rid.endswith("/"):
                        continue
                    if not (exists("tags/block", rid, ".json") or exists("tags/item", rid, ".json")
                            or exists("tags/function", rid, ".json")):
                        errors.append(f"{rel}:{n}: missing tag #{rid}")
                if line.startswith("$") and "$(" not in line:
                    errors.append(f"{rel}:{n}: macro line without $(...) variable")
                if "$(" in line and not line.startswith("$"):
                    errors.append(f"{rel}:{n}: $(...) used outside a macro line")


NEW_IN_26_3 = ("poplar", "shelf_mushroom")


def check_version_only_ids(errors: list[str]) -> None:
    base = PACK / "data"
    for f in base.rglob("*"):
        if not f.is_file() or f.suffix not in (".mcfunction", ".json"):
            continue
        for n, line in enumerate(f.read_text().splitlines(), 1):
            for needle in NEW_IN_26_3:
                if needle in line and '"required": false' not in line and not line.lstrip().startswith("#"):
                    errors.append(f"{f.relative_to(ROOT)}:{n}: 26.3-only id '{needle}' in base pack (move to overlay_26_3)")


def check_overlay_shadows(errors: list[str]) -> None:
    for root in resource_roots()[1:]:
        for f in (root / "data").rglob("*.mcfunction"):
            rel = f.relative_to(root)
            if not (PACK / rel).exists():
                errors.append(f"{f.relative_to(ROOT)}: overlay function has no base version (26.2 would miss it)")


def check_constants(errors: list[str]) -> None:
    """Numeric fake players (#16, #9000...) read by functions must be set in load."""
    load = (PACK / "data" / NAMESPACE / "function/load.mcfunction").read_text()
    set_in_load = set(re.findall(r"scoreboard players set (#-?\d+) " + NAMESPACE + r"\.data", load))
    for root in resource_roots():
        for f in (root / "data").rglob("*.mcfunction"):
            for c in sorted(set(re.findall(r"(#-?\d+) " + NAMESPACE + r"\.data", f.read_text())) - set_in_load):
                errors.append(f"{f.relative_to(ROOT)}: constant {c} is never set in load")


def build(ver: str) -> Path:
    DIST.mkdir(exist_ok=True)
    out = DIST / f"Timber-{ver}.zip"
    for old in DIST.glob("Timber-*.zip"):
        old.unlink()
    files = sorted(p for p in PACK.rglob("*") if p.is_file())
    files.append(ROOT / "LICENSE")
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for f in files:
            arc = f.name if f.parent == ROOT else f.relative_to(PACK).as_posix()
            info = zipfile.ZipInfo(arc, date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            z.writestr(info, f.read_bytes())
    return out


def main() -> None:
    errors: list[str] = []
    check_json(errors)
    check_refs(errors)
    check_version_only_ids(errors)
    check_overlay_shadows(errors)
    check_constants(errors)
    if errors:
        print("\n".join(errors))
        sys.exit(f"{len(errors)} problem(s)")
    ver = version()
    for rel, needle in (("pack/pack.mcmeta", f"v{ver}"), ("pack/data/timber/function/settings.mcfunction", f"v{ver}"),
                        ("pack/data/timber/function/uninstall.mcfunction", f"Timber-{ver}.zip"),
                        ("CHANGELOG.md", f"## {ver}")):
        if needle not in (ROOT / rel).read_text():
            sys.exit(f"{rel}: expected '{needle}' (version mismatch)")
    out = build(ver)
    nfunc = sum(1 for _ in PACK.rglob("*.mcfunction"))
    print(f"OK  {nfunc} functions, refs resolved -> {out.relative_to(ROOT)} ({out.stat().st_size} bytes)")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
import argparse
import json
import sys
import zipfile
from collections import defaultdict
from pathlib import Path

MIB = 1024 * 1024


def group_for(name: str) -> str:
    if name.startswith("lib/"):
        return "native"
    if name.endswith(".dex") and "/" not in name:
        return "dex"
    if name == "resources.arsc" or name.startswith("res/"):
        return "resources"
    if name.startswith("assets/"):
        return "assets"
    if name.startswith("META-INF/"):
        return "meta-inf"
    return "other"


def abi_for(name: str):
    parts = name.split("/")
    if len(parts) >= 3 and parts[0] == "lib":
        return parts[1]
    return None


def analyze(path: Path):
    groups = defaultdict(lambda: {"compressed": 0, "uncompressed": 0, "entries": 0})
    abis = defaultdict(lambda: {"compressed": 0, "uncompressed": 0, "entries": 0})
    libs = defaultdict(lambda: {"compressed": 0, "uncompressed": 0, "entries": 0})
    top = []
    with zipfile.ZipFile(path) as zf:
        bad = zf.testzip()
        for info in zf.infolist():
            group = group_for(info.filename)
            groups[group]["compressed"] += info.compress_size
            groups[group]["uncompressed"] += info.file_size
            groups[group]["entries"] += 1
            abi = abi_for(info.filename)
            if abi:
                abis[abi]["compressed"] += info.compress_size
                abis[abi]["uncompressed"] += info.file_size
                abis[abi]["entries"] += 1
                key = f"{abi}/{Path(info.filename).name}"
                libs[key]["compressed"] += info.compress_size
                libs[key]["uncompressed"] += info.file_size
                libs[key]["entries"] += 1
            top.append({"path": info.filename, "compressed": info.compress_size, "uncompressed": info.file_size})
    top.sort(key=lambda item: (item["uncompressed"], item["compressed"]), reverse=True)
    return {
        "apk": str(path),
        "file_bytes": path.stat().st_size,
        "file_mib": path.stat().st_size / MIB,
        "zip_compressed_entry_bytes": sum(item["compressed"] for item in top),
        "zip_uncompressed_entry_bytes": sum(item["uncompressed"] for item in top),
        "zip_integrity": "ok" if bad is None else f"bad-entry:{bad}",
        "groups": dict(sorted(groups.items(), key=lambda pair: pair[1]["uncompressed"], reverse=True)),
        "abis": dict(sorted(abis.items(), key=lambda pair: pair[1]["uncompressed"], reverse=True)),
        "native_libraries": dict(sorted(libs.items(), key=lambda pair: pair[1]["uncompressed"], reverse=True)),
        "top_entries": top[:25],
    }


def fmt_bytes(value: int) -> str:
    return f"{value:,} B ({value / MIB:.2f} MiB)"


def markdown(report, budget_mib):
    lines = [
        "# APK size report",
        "",
        f"- APK: `{Path(report['apk']).name}`",
        f"- File size: **{fmt_bytes(report['file_bytes'])}**",
        f"- ZIP entry compressed sum: {fmt_bytes(report['zip_compressed_entry_bytes'])}",
        f"- ZIP entry uncompressed sum: {fmt_bytes(report['zip_uncompressed_entry_bytes'])}",
        f"- ZIP integrity: **{report['zip_integrity']}**",
    ]
    if budget_mib is not None:
        status = "PASS" if report["file_mib"] <= budget_mib else "FAIL"
        lines += [f"- Budget: **{budget_mib:.2f} MiB**", f"- Budget status: **{status}**"]
    lines += ["", "## Groups", "", "| Group | Compressed | Uncompressed | Entries |", "|---|---:|---:|---:|"]
    for name, data in report["groups"].items():
        lines.append(f"| {name} | {data['compressed']/MIB:.2f} MiB | {data['uncompressed']/MIB:.2f} MiB | {data['entries']} |")
    lines += ["", "## Native ABIs", "", "| ABI | Compressed | Uncompressed | Entries |", "|---|---:|---:|---:|"]
    for name, data in report["abis"].items():
        lines.append(f"| {name} | {data['compressed']/MIB:.2f} MiB | {data['uncompressed']/MIB:.2f} MiB | {data['entries']} |")
    lines += ["", "## Native libraries", "", "| ABI/library | Compressed | Uncompressed |", "|---|---:|---:|"]
    for name, data in report["native_libraries"].items():
        lines.append(f"| `{name}` | {data['compressed']/MIB:.2f} MiB | {data['uncompressed']/MIB:.2f} MiB |")
    lines += ["", "## Largest entries", "", "| Path | Compressed | Uncompressed |", "|---|---:|---:|"]
    for item in report["top_entries"]:
        lines.append(f"| `{item['path']}` | {item['compressed']/MIB:.2f} MiB | {item['uncompressed']/MIB:.2f} MiB |")
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    parser.add_argument("--budget-mib", type=float)
    parser.add_argument("--json-output", type=Path)
    parser.add_argument("--markdown-output", type=Path)
    args = parser.parse_args()
    report = analyze(args.apk)
    text = markdown(report, args.budget_mib)
    print(text)
    if args.json_output:
        args.json_output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    if args.markdown_output:
        args.markdown_output.write_text(text, encoding="utf-8")
    if report["zip_integrity"] != "ok":
        return 2
    if args.budget_mib is not None and report["file_mib"] > args.budget_mib:
        return 3
    return 0


if __name__ == "__main__":
    sys.exit(main())

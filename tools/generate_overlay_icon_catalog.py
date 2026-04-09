#!/usr/bin/env python3
# Copyright (C) 2026 DerpFest / AxThemeStore
# SPDX-License-Identifier: Apache-2.0
#
# Scans vendor overlay RRO trees and writes res/raw/overlay_icon_catalog.json
# for a fully offline catalog (no runtime APK enumeration).
#
# Scans under --vendor-overlay:
#   Icons/           — Wi‑Fi, signal, mobile data icon overlays
#   themes/battery/  — battery shape (android.theme.customization.battery_style)
#
# Usage (from AxThemeStore):
#   python3 tools/generate_overlay_icon_catalog.py \
#     --vendor-overlay /path/to/vendor/overlay \
#     --out res/raw/overlay_icon_catalog.json

from __future__ import annotations

import argparse
import json
import os
import sys
import xml.etree.ElementTree as ET

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"

WIFI_CAT = "android.theme.customization.wifi_icon"
SIGNAL_CAT = "android.theme.customization.signal_icon"
DATA_CAT = "android.customization.sb_data"
BATTERY_CAT = "android.theme.customization.battery_style"


def parse_manifest(path: str) -> dict | None:
    try:
        tree = ET.parse(path)
        root = tree.getroot()
    except ET.ParseError as e:
        print(f"WARN: skip {path}: {e}", file=sys.stderr)
        return None

    pkg = root.get("package")
    if not pkg:
        return None

    category = None
    label = None
    for el in root.iter():
        tag = el.tag.split("}")[-1] if "}" in el.tag else el.tag
        if tag == "overlay":
            category = el.get(ANDROID_NS + "category") or el.get("category")
        elif tag == "application":
            label = el.get(ANDROID_NS + "label") or el.get("label")

    if category not in (WIFI_CAT, SIGNAL_CAT, DATA_CAT, BATTERY_CAT):
        return None

    if not label:
        label = pkg.rsplit(".", 1)[-1]

    if category == WIFI_CAT:
        kind = "wifi"
    elif category == SIGNAL_CAT:
        kind = "signal"
    elif category == DATA_CAT:
        kind = "data"
    else:
        kind = "battery"
    return {
        "package": pkg,
        "label": label,
        "kind": kind,
        "customizationKey": category,
    }


def scan(icons_root: str) -> list[dict]:
    entries: list[dict] = []
    for dirpath, _dirnames, filenames in os.walk(icons_root):
        if "AndroidManifest.xml" not in filenames:
            continue
        mpath = os.path.join(dirpath, "AndroidManifest.xml")
        info = parse_manifest(mpath)
        if info:
            entries.append(info)

    # Stable order: Wi‑Fi, signal, data, battery; by label
    kind_order = {"wifi": 0, "signal": 1, "data": 2, "battery": 3}
    entries.sort(
        key=lambda e: (kind_order.get(e["kind"], 9), e["label"].lower())
    )
    return entries


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument(
        "--vendor-overlay",
        help="Path to vendor/overlay (scans Icons/ and themes/battery/)",
    )
    ap.add_argument(
        "--icons-root",
        help="Only scan vendor/overlay/Icons (omit themes/battery)",
    )
    ap.add_argument(
        "--out",
        required=True,
        help="Output JSON path (e.g. res/raw/overlay_icon_catalog.json)",
    )
    args = ap.parse_args()

    if args.icons_root and args.vendor_overlay:
        print("ERROR: use either --vendor-overlay or --icons-root, not both", file=sys.stderr)
        return 1

    if not args.vendor_overlay and not args.icons_root:
        print("ERROR: pass --vendor-overlay or --icons-root", file=sys.stderr)
        return 1

    entries: list[dict] = []
    if args.vendor_overlay:
        vo = os.path.abspath(args.vendor_overlay)
        if not os.path.isdir(vo):
            print(f"ERROR: not a directory: {vo}", file=sys.stderr)
            return 1
        icons = os.path.join(vo, "Icons")
        if os.path.isdir(icons):
            entries.extend(scan(icons))
        else:
            print(f"WARN: missing Icons under {vo}", file=sys.stderr)
        battery = os.path.join(vo, "themes", "battery")
        if os.path.isdir(battery):
            entries.extend(scan(battery))
        else:
            print(f"WARN: missing themes/battery under {vo}", file=sys.stderr)
    else:
        if not os.path.isdir(args.icons_root):
            print(f"ERROR: not a directory: {args.icons_root}", file=sys.stderr)
            return 1
        entries = scan(args.icons_root)

    kind_order = {"wifi": 0, "signal": 1, "data": 2, "battery": 3}
    entries.sort(
        key=lambda e: (kind_order.get(e["kind"], 9), e["label"].lower())
    )

    out_dir = os.path.dirname(args.out)
    if out_dir:
        os.makedirs(out_dir, exist_ok=True)

    doc = {
        "version": 1,
        "generatedNote": "Regenerate with tools/generate_overlay_icon_catalog.py",
        "entries": entries,
    }
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(doc, f, indent=2, ensure_ascii=False)
        f.write("\n")

    print(f"Wrote {len(entries)} entries to {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Build the bounded P6.2 review package from canonical API-37 native captures."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw


CELL = 160
TYPES = ("pawn", "rook", "knight", "bishop", "queen", "king")
BACKGROUNDS = (
    (231, 230, 200),
    (78, 129, 145),
    (231, 230, 200),
    (78, 129, 145),
)
TARGETS = {
    "pawn": (76, 116),
    "rook": (94, 126),
    "knight": (86, 133),
    "bishop": (84, 137),
    "queen": (92, 143),
    "king": (90, 146),
}


def piece_mask(cell: Image.Image, background: tuple[int, int, int], threshold: int = 8) -> Image.Image:
    background_image = Image.new("RGB", cell.size, background)
    difference = ImageChops.difference(cell.convert("RGB"), background_image)
    return difference.convert("L").point(lambda value: 255 if value >= threshold else 0)


def metrics(cell: Image.Image, background: tuple[int, int, int]) -> dict[str, float | int]:
    mask = piece_mask(cell, background)
    box = mask.getbbox()
    if box is None:
        raise AssertionError("piece cell has no painted pixels")
    left, top, right, bottom = box
    pixels = mask.load()
    count = 0
    sum_x = 0.0
    sum_y = 0.0
    for y in range(top, bottom):
        for x in range(left, right):
            if pixels[x, y]:
                count += 1
                sum_x += x + 0.5
                sum_y += y + 0.5
    return {
        "left": left,
        "top": top,
        "right": right,
        "bottom": bottom,
        "width": right - left,
        "height": bottom - top,
        "centroidX": round(sum_x / count, 3),
        "centroidY": round(sum_y / count, 3),
        "leftMargin": left,
        "rightMargin": CELL - right,
        "topMargin": top,
        "bottomMargin": CELL - bottom,
        "bottomBaseline": bottom - 1,
    }


def label_sheet(left: Image.Image, right: Image.Image, left_label: str, right_label: str) -> Image.Image:
    gap = 12
    label_height = 30
    canvas = Image.new("RGB", (left.width + gap + right.width, label_height + max(left.height, right.height)), (7, 9, 10))
    draw = ImageDraw.Draw(canvas)
    draw.text((8, 9), left_label, fill=(214, 223, 226))
    draw.text((left.width + gap + 8, 9), right_label, fill=(214, 223, 226))
    canvas.paste(left, (0, label_height))
    canvas.paste(right, (left.width + gap, label_height))
    return canvas


def find_reference_contact(reference: Image.Image) -> Image.Image:
    # The approved A-S2 sheet carries an exact 6 x 4 grid of 160 px cells.
    expected = (176, 88, 1136, 728)
    contact = reference.crop(expected).convert("RGB")
    if contact.size != (CELL * 6, CELL * 4):
        raise AssertionError((contact.size, expected))
    return contact


def save_piece_crops(contact: Image.Image, output: Path) -> None:
    for index, piece_type in enumerate(TYPES):
        white_dark = contact.crop((index * CELL, CELL, (index + 1) * CELL, CELL * 2))
        black_light = contact.crop((index * CELL, CELL * 2, (index + 1) * CELL, CELL * 3))
        pair = Image.new("RGB", (CELL * 2, CELL), (7, 9, 10))
        pair.paste(white_dark, (0, 0))
        pair.paste(black_light, (CELL, 0))
        pair.save(output / f"p6-native-crop-{piece_type}.png", optimize=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--captures", type=Path, required=True)
    parser.add_argument("--reference", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)

    native = Image.open(args.captures / "p6-contact-sheet.png").convert("RGB")
    if native.size != (CELL * 6, CELL * 4):
        raise AssertionError(f"native contact sheet must be 960x640, got {native.size}")
    reference = find_reference_contact(Image.open(args.reference).convert("RGB"))

    label_sheet(reference, native, "APPROVED A-S2 DESIGN", "NATIVE COMPOSE / API-37").save(
        args.output / "p6-a-s2-reference-vs-native-contact.png",
        optimize=True,
    )
    native_white = native.crop((0, 0, native.width, CELL))
    reference_white = reference.crop((0, 0, reference.width, CELL))
    label_sheet(reference_white, native_white, "APPROVED WHITE / LIGHT", "NATIVE WHITE / LIGHT").save(
        args.output / "p6-a-s2-reference-vs-native-white-light.png",
        optimize=True,
    )

    report: dict[str, object] = {
        "cellPx": CELL,
        "targets": {key: {"width": value[0], "height": value[1]} for key, value in TARGETS.items()},
        "pieces": {},
    }
    for index, piece_type in enumerate(TYPES):
        reference_cell = reference.crop((index * CELL, CELL * 2, (index + 1) * CELL, CELL * 3))
        native_cell = native.crop((index * CELL, CELL * 2, (index + 1) * CELL, CELL * 3))
        reference_metrics = metrics(reference_cell, BACKGROUNDS[2])
        native_metrics = metrics(native_cell, BACKGROUNDS[2])
        target_width, target_height = TARGETS[piece_type]
        width_delta = int(native_metrics["width"]) - target_width
        height_delta = int(native_metrics["height"]) - target_height
        if abs(width_delta) > 2 or abs(height_delta) > 2:
            raise AssertionError(f"{piece_type} native bounds miss A-S2 target: {native_metrics}")
        report["pieces"][piece_type] = {
            "reference": reference_metrics,
            "native": native_metrics,
            "targetDelta": {"width": width_delta, "height": height_delta},
            "referenceDelta": {
                "width": int(native_metrics["width"]) - int(reference_metrics["width"]),
                "height": int(native_metrics["height"]) - int(reference_metrics["height"]),
                "centroidX": round(float(native_metrics["centroidX"]) - float(reference_metrics["centroidX"]), 3),
                "baseline": int(native_metrics["bottomBaseline"]) - int(reference_metrics["bottomBaseline"]),
            },
        }
    (args.output / "p6-native-painted-bounds.json").write_text(json.dumps(report, indent=2) + "\n")
    save_piece_crops(native, args.output)


if __name__ == "__main__":
    main()

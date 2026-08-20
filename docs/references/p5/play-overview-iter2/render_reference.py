from __future__ import annotations

import base64
import hashlib
import os
import shutil
import sys
from pathlib import Path

from PIL import Image
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parents[3]

SOURCE_SHA256 = {
    "index.html": "c273cbc7c52187f6d0e3a0fd715905c0a1298e85bbd23becf5c973e738a94086",
    "styles.css": "a298588b5e24b769148e27e9dd064531c0d2e91384c6de12236abaf2b7187ccf",
    "prototype.js": "20c553207d470d02676940bb7f039e8afbaed1d0d497efcc3ca73d8cc02b2070",
}
HEROES = {
    "rook-native-render-extract.png": (
        REPO / "app/src/main/assets/play-overview/lumen_play_vs_engine_hero.png",
        "43a6accd71c5f9f1bfba552e0c409f5a95f25b1567b617c5c8851b5186d40e00",
    ),
    "knight-native-render-extract.png": (
        REPO / "app/src/main/assets/play-overview/lumen_engine_arena_hero.png",
        "2554fb301501a9f667652ab0631147bd7b38d868812b2dfecc0ea5bfa0aa12f2",
    ),
}


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def verify_frozen_source() -> tuple[str, str, str]:
    parts = []
    for name, expected in SOURCE_SHA256.items():
        data = (ROOT / name).read_bytes()
        actual = sha256(data)
        if actual != expected:
            raise AssertionError(f"Frozen Play Iteration 2 source changed: {name}: {actual}")
        parts.append(data.decode("utf-8"))
    return tuple(parts)  # type: ignore[return-value]


def canonical_data_uri(path: Path, expected_sha: str) -> str:
    data = path.read_bytes()
    actual = sha256(data)
    if actual != expected_sha:
        raise AssertionError(f"Canonical hero asset changed: {path}: {actual}")
    image = Image.open(path)
    if image.size != (1254, 1254):
        raise AssertionError(f"Canonical hero dimensions changed: {path}: {image.size}")
    return "data:image/png;base64," + base64.b64encode(data).decode("ascii")


def browser_executable() -> str:
    explicit = os.environ.get("P5_BROWSER")
    if explicit:
        return explicit
    for candidate in ("google-chrome", "chromium", "chromium-browser"):
        resolved = shutil.which(candidate)
        if resolved:
            return resolved
    raise RuntimeError("No system Chromium/Chrome executable found for frozen Play reference rendering")


def main() -> None:
    output = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "play-overview-approved-canonical.png"
    output.parent.mkdir(parents=True, exist_ok=True)

    html, css, js = verify_frozen_source()
    document = html.replace(
        '<link rel="stylesheet" href="styles.css" />',
        f"<style>{css}</style>",
    ).replace(
        '<script src="prototype.js"></script>',
        f"<script>{js}</script>",
    )

    # Canonicalization is intentionally restricted to the two hero-image source pixels.
    # Frozen HTML/CSS/JS geometry, typography, lighting and spacing remain byte-verified above.
    for provisional_name, (canonical_path, expected_sha) in HEROES.items():
        document = document.replace(
            f"assets/{provisional_name}",
            canonical_data_uri(canonical_path, expected_sha),
        )
    document = document.replace("<body>", '<body class="view-play">', 1)

    executable = browser_executable()
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(
            headless=True,
            executable_path=executable,
            args=["--no-sandbox", "--disable-gpu", "--disable-dev-shm-usage"],
        )
        page = browser.new_page(viewport={"width": 390, "height": 844}, device_scale_factor=1)
        page.set_content(document, wait_until="load")
        page.screenshot(path=str(output), full_page=False)
        page.close()
        browser.close()

    rendered = Image.open(output).convert("RGB")
    if rendered.size != (390, 844):
        raise AssertionError(f"Canonicalized Play reference must be 390x844, got {rendered.size}")
    print(f"Canonicalized Play reference: {output}")
    print(f"Decoded RGB SHA-256: {sha256(rendered.tobytes())}")
    for _, (path, expected) in HEROES.items():
        print(f"Canonical hero SHA-256: {path.relative_to(REPO)} = {expected}")


if __name__ == "__main__":
    main()

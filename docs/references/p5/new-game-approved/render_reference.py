from __future__ import annotations

import hashlib
import os
import re
import shutil
import sys
from pathlib import Path

from PIL import Image
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parent

SOURCE_SHA256 = {
    "index.html": "62ea6e1d6e7374022c5c8c3572480dbb072931faf8b5899bc98730e2d49e96b4",
    "styles.css": "d966b16da1861e76e368dcb8ba7883abf1ed751936d6dc277d574ac438436a17",
    "prototype.js": "a76af30b331fbb623ba4f42f9a6a3aa78d5814e9d356e9aadedad6ff01b64ade",
}
APPROVED_PNG_SHA256 = "e07580cebf4579db9c06bebe44bcabd224cc3f3489a9a79bc18d2043ada1ab8e"
APPROVED_RGB_SHA256 = "c95f60918335d222b60d9f6f98884be598948a751d3f496c3b1a56728775b357"


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def verify_frozen_source() -> tuple[str, str, str]:
    parts: list[str] = []
    for name, expected in SOURCE_SHA256.items():
        data = (ROOT / name).read_bytes()
        actual = sha256(data)
        if actual != expected:
            raise AssertionError(f"Frozen New Game source changed: {name}: {actual}")
        parts.append(data.decode("utf-8"))
    return tuple(parts)  # type: ignore[return-value]


def browser_executable() -> str:
    explicit = os.environ.get("P5_BROWSER")
    if explicit:
        return explicit
    for candidate in ("google-chrome", "chromium", "chromium-browser"):
        resolved = shutil.which(candidate)
        if resolved:
            return resolved
    raise RuntimeError("No system Chromium/Chrome executable found for frozen New Game reference rendering")


def main() -> None:
    output = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "new-game-approved-canonical.png"
    output.parent.mkdir(parents=True, exist_ok=True)

    html, css, js = verify_frozen_source()
    document = re.sub(
        r'<link rel="stylesheet" href="styles.css"\s*/?>',
        f"<style>{css}</style>",
        html,
    ).replace(
        '<script src="prototype.js"></script>',
        f"<script>{js}</script>",
    )

    executable = browser_executable()
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(
            headless=True,
            executable_path=executable,
            args=["--no-sandbox", "--disable-gpu", "--disable-dev-shm-usage"],
        )
        page = browser.new_page(viewport={"width": 390, "height": 844}, device_scale_factor=1)
        page.set_content(document, wait_until="load")
        page.evaluate("document.body.classList.add('view-phone')")
        page.screenshot(path=str(output), full_page=False)
        page.close()
        browser.close()

    png_bytes = output.read_bytes()
    png_sha = sha256(png_bytes)
    if png_sha != APPROVED_PNG_SHA256:
        raise AssertionError(f"Approved New Game PNG changed: {png_sha}")

    rendered = Image.open(output).convert("RGB")
    if rendered.size != (390, 844):
        raise AssertionError(f"Approved New Game reference must be 390x844, got {rendered.size}")
    rgb_sha = sha256(rendered.tobytes())
    if rgb_sha != APPROVED_RGB_SHA256:
        raise AssertionError(f"Approved New Game RGB pixels changed: {rgb_sha}")

    print(f"Approved New Game reference: {output}")
    for name, expected in SOURCE_SHA256.items():
        print(f"Frozen source SHA-256: {name} = {expected}")
    print(f"Approved PNG SHA-256: {png_sha}")
    print(f"Decoded RGB SHA-256: {rgb_sha}")


if __name__ == "__main__":
    main()

from __future__ import annotations

import hashlib
import os
import re
import shlex
import shutil
import subprocess
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
PINNED_ACTIONS_IMAGE = "debian:13.3-slim"
PINNED_DEBIAN_CHROMIUM = "144.0.7559.96-1~deb13u1"


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


def ensure_inter_font() -> Path:
    fc_match = shutil.which("fc-match")
    if fc_match:
        family = subprocess.check_output(
            [fc_match, "-f", "%{family}", "Inter"],
            text=True,
        ).strip()
        if family.split(",", 1)[0].strip() == "Inter":
            font_file = subprocess.check_output(
                [fc_match, "-f", "%{file}", "Inter"],
                text=True,
            ).strip()
            print(f"Resolved reference font: {family}")
            return Path(font_file).resolve().parent

    if os.environ.get("GITHUB_ACTIONS") != "true":
        raise RuntimeError(
            "Frozen New Game reference requires the Inter font family used by the approved render"
        )

    subprocess.run(["sudo", "apt-get", "update", "-qq"], check=True)
    subprocess.run(
        ["sudo", "apt-get", "install", "-y", "-qq", "fonts-inter=4.0+ds-1"],
        check=True,
    )
    fc_cache = shutil.which("fc-cache")
    if fc_cache:
        subprocess.run([fc_cache, "-f"], check=True)

    fc_match = shutil.which("fc-match")
    if not fc_match:
        raise RuntimeError("fontconfig fc-match unavailable after installing pinned Inter")
    family = subprocess.check_output(
        [fc_match, "-f", "%{family}", "Inter"],
        text=True,
    ).strip()
    if family.split(",", 1)[0].strip() != "Inter":
        raise RuntimeError(f"Pinned Inter did not resolve through fontconfig: {family!r}")
    font_file = subprocess.check_output(
        [fc_match, "-f", "%{file}", "Inter"],
        text=True,
    ).strip()
    print(f"Resolved reference font: {family}")
    return Path(font_file).resolve().parent


def browser_executable() -> str:
    explicit = os.environ.get("P5_BROWSER")
    if explicit:
        return explicit
    for candidate in ("google-chrome", "chromium", "chromium-browser"):
        resolved = shutil.which(candidate)
        if resolved:
            return resolved
    raise RuntimeError("No system Chromium/Chrome executable found for frozen New Game reference rendering")


def render_in_pinned_actions_container(output: Path, font_dir: Path) -> None:
    docker = shutil.which("docker")
    if not docker:
        raise RuntimeError("Docker is required for the pinned New Game reference renderer on GitHub Actions")
    repo_root = ROOT.parents[3]
    try:
        output_relative = output.resolve().relative_to(repo_root.resolve())
    except ValueError as exc:
        raise RuntimeError(f"Reference output must stay inside the repository: {output}") from exc

    inner = " && ".join(
        [
            "apt-get update -qq",
            (
                "DEBIAN_FRONTEND=noninteractive apt-get install -y -qq "
                f"chromium={shlex.quote(PINNED_DEBIAN_CHROMIUM)} python3 python3-pip fontconfig"
            ),
            "fc-cache -f",
            "python3 -m pip install --quiet --disable-pip-version-check --break-system-packages pillow playwright",
            (
                "P5_PINNED_CONTAINER=1 P5_BROWSER=/usr/bin/chromium "
                "python3 docs/references/p5/new-game-approved/render_reference.py "
                + shlex.quote(str(output_relative))
            ),
        ]
    )
    subprocess.run(
        [
            docker,
            "run",
            "--rm",
            "-v",
            f"{repo_root.resolve()}:/work",
            "-v",
            f"{font_dir.resolve()}:/usr/share/fonts/opentype/inter:ro",
            "-w",
            "/work",
            PINNED_ACTIONS_IMAGE,
            "bash",
            "-lc",
            inner,
        ],
        check=True,
    )


def verify_rendered_output(output: Path) -> tuple[str, str]:
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
    return png_sha, rgb_sha


def main() -> None:
    output = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "new-game-approved-canonical.png"
    output.parent.mkdir(parents=True, exist_ok=True)

    html, css, js = verify_frozen_source()
    font_dir = ensure_inter_font()

    if os.environ.get("GITHUB_ACTIONS") == "true" and os.environ.get("P5_PINNED_CONTAINER") != "1":
        render_in_pinned_actions_container(output, font_dir)
        png_sha, rgb_sha = verify_rendered_output(output)
    else:
        document = re.sub(
            r'<link rel="stylesheet" href="styles.css"\s*/?>',
            f"<style>{css}</style>",
            html,
        ).replace(
            '<script src="prototype.js"></script>',
            f"<script>{js}</script>",
        )

        executable = browser_executable()
        version = subprocess.check_output([executable, "--version"], text=True).strip()
        print(f"Resolved reference browser: {version}")
        with sync_playwright() as playwright:
            browser = playwright.chromium.launch(
                headless=True,
                executable_path=executable,
                args=["--no-sandbox"],
            )
            page = browser.new_page(viewport={"width": 390, "height": 844}, device_scale_factor=1)
            page.set_content(document, wait_until="load")
            page.evaluate("document.body.classList.add('view-phone')")
            page.screenshot(path=str(output), full_page=False)
            page.close()
            browser.close()
        png_sha, rgb_sha = verify_rendered_output(output)

    print(f"Approved New Game reference: {output}")
    for name, expected in SOURCE_SHA256.items():
        print(f"Frozen source SHA-256: {name} = {expected}")
    print(f"Approved PNG SHA-256: {png_sha}")
    print(f"Decoded RGB SHA-256: {rgb_sha}")


if __name__ == "__main__":
    main()

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
APPROVED_INTER_REGULAR_SHA256 = "d4f2b9e148059a15f014cb0f0b8fea8cd11bfa447dd483bedf1b0adc0e2ba799"
PINNED_ACTIONS_IMAGE = "debian:13.3-slim"
PINNED_DEBIAN_PACKAGES = (
    ("chromium-common", "144.0.7559.96-1~deb13u1", "amd64"),
    ("chromium-sandbox", "144.0.7559.96-1~deb13u1", "amd64"),
    ("chromium", "144.0.7559.96-1~deb13u1", "amd64"),
    ("libfreetype6", "2.13.3+dfsg-1", "amd64"),
    ("libharfbuzz0b", "10.2.0-1+b1", "amd64"),
    ("libharfbuzz-subset0", "10.2.0-1+b1", "amd64"),
    ("fonts-inter", "4.1+ds-1", "all"),
)


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
        family = subprocess.check_output([fc_match, "-f", "%{family}", "Inter"], text=True).strip()
        if family.split(",", 1)[0].strip() == "Inter":
            font_file = Path(
                subprocess.check_output([fc_match, "-f", "%{file}", "Inter"], text=True).strip()
            ).resolve()
            actual = sha256(font_file.read_bytes())
            if actual != APPROVED_INTER_REGULAR_SHA256:
                raise RuntimeError(f"Inter Regular identity changed: {font_file}: {actual}")
            print(f"Resolved reference font: {family}")
            print(f"Resolved Inter Regular SHA-256: {actual}")
            return font_file.parent

    if os.environ.get("GITHUB_ACTIONS") != "true":
        raise RuntimeError("Frozen New Game reference requires the approved Inter Regular font")

    subprocess.run(["sudo", "apt-get", "update", "-qq"], check=True)
    subprocess.run(["sudo", "apt-get", "install", "-y", "-qq", "fonts-inter=4.0+ds-1"], check=True)
    if shutil.which("fc-cache"):
        subprocess.run(["fc-cache", "-f"], check=True)
    return ensure_inter_font()


def browser_executable() -> str:
    explicit = os.environ.get("P5_BROWSER")
    if explicit:
        return explicit
    for candidate in ("google-chrome", "chromium", "chromium-browser"):
        resolved = shutil.which(candidate)
        if resolved:
            return resolved
    raise RuntimeError("No system Chromium/Chrome executable found for frozen New Game reference rendering")


def render_in_pinned_actions_container(output: Path) -> None:
    docker = shutil.which("docker")
    if not docker:
        raise RuntimeError("Docker is required for the pinned New Game reference renderer on GitHub Actions")
    repo_root = ROOT.parents[3]
    try:
        output_relative = output.resolve().relative_to(repo_root.resolve())
    except ValueError as exc:
        raise RuntimeError(f"Reference output must stay inside the repository: {output}") from exc

    specs = "\n".join("|".join(spec) for spec in PINNED_DEBIAN_PACKAGES)
    inner = f"""
set -euo pipefail
apt-get update -qq
DEBIAN_FRONTEND=noninteractive apt-get install -y -qq ca-certificates curl python3 python3-pip fontconfig dpkg-dev
cat >/tmp/p5-package-specs <<'SPECS'
{specs}
SPECS
fetch_snapshot_deb() {{
  name="$1"; version="$2"; arch="$3"; out="/tmp/${{name}}.deb"
  enc_version="$(python3 -c 'import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1], safe=""))' "$version")"
  api="https://snapshot.debian.org/mr/binary/${{name}}/${{enc_version}}/binfiles?fileinfo=1"
  curl --fail --location --retry 3 --silent --show-error "$api" -o /tmp/p5-info.json
  file_hash="$(python3 - "$name" "$arch" <<'PY'
import json, sys
name, arch = sys.argv[1:]
data = json.load(open('/tmp/p5-info.json'))
matches = [r['hash'] for r in data['result'] if r['architecture'] == arch]
if len(matches) != 1:
    raise SystemExit(f'{{name}}: expected one {{arch}} snapshot file, got {{matches}}')
print(matches[0])
PY
)"
  echo "Snapshot package: $name $version $arch sha1=$file_hash"
  python3 - "$file_hash" <<'PY'
import json, sys
h = sys.argv[1]
data = json.load(open('/tmp/p5-info.json'))
for item in data.get('fileinfo', {{}}).get(h, []):
    print('Snapshot provenance: ' + item['archive_name'] + ' ' + item['first_seen'] + item['path'] + '/' + item['name'])
PY
  curl --fail --location --retry 3 --silent --show-error "https://snapshot.debian.org/file/${{file_hash}}" -o "$out"
  test "$(dpkg-deb -f "$out" Package)" = "$name"
  test "$(dpkg-deb -f "$out" Version)" = "$version"
  test "$(dpkg-deb -f "$out" Architecture)" = "$arch"
  echo "Snapshot package SHA-256: $(sha256sum "$out" | cut -d' ' -f1) $name"
}}
while IFS='|' read -r name version arch; do fetch_snapshot_deb "$name" "$version" "$arch"; done </tmp/p5-package-specs
DEBIAN_FRONTEND=noninteractive apt-get install -y -qq /tmp/chromium-common.deb /tmp/chromium-sandbox.deb /tmp/chromium.deb /tmp/fonts-inter.deb
dpkg -i /tmp/libfreetype6.deb /tmp/libharfbuzz0b.deb /tmp/libharfbuzz-subset0.deb
while IFS='|' read -r name version arch; do test "$(dpkg-query -W -f='${{Version}}' "$name")" = "$version"; done </tmp/p5-package-specs
test "$(dpkg --print-architecture)" = amd64
chromium --version
fc-cache -f
INTER_FILE="$(fc-match -f '%{{file}}' Inter)"
test "$(fc-match -f '%{{family}}' Inter | cut -d, -f1)" = Inter
echo "{APPROVED_INTER_REGULAR_SHA256}  $INTER_FILE" | sha256sum -c -
python3 -m pip install --quiet --disable-pip-version-check --break-system-packages pillow playwright
P5_PINNED_CONTAINER=1 P5_BROWSER=/usr/bin/chromium python3 docs/references/p5/new-game-approved/render_reference.py {shlex.quote(str(output_relative))}
"""
    subprocess.run(
        [docker, "run", "--rm", "-v", f"{repo_root.resolve()}:/work", "-w", "/work", PINNED_ACTIONS_IMAGE, "bash", "-lc", inner],
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
    if os.environ.get("GITHUB_ACTIONS") == "true" and os.environ.get("P5_PINNED_CONTAINER") != "1":
        render_in_pinned_actions_container(output)
        png_sha, rgb_sha = verify_rendered_output(output)
    else:
        ensure_inter_font()
        document = re.sub(r'<link rel="stylesheet" href="styles.css"\s*/?>', f"<style>{css}</style>", html).replace('<script src="prototype.js"></script>', f"<script>{js}</script>")
        executable = browser_executable()
        version = subprocess.check_output([executable, "--version"], text=True).strip()
        print(f"Resolved reference browser: {version}")
        with sync_playwright() as playwright:
            browser = playwright.chromium.launch(headless=True, executable_path=executable, args=["--no-sandbox"])
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

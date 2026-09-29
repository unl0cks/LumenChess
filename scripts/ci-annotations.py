#!/usr/bin/env python3
"""Publishes CI evidence as workflow annotations.

Annotations are attached to the check run and readable through the public check-runs API, so a
failure can be inspected without downloading logs or artifacts. (Messages are cut to 4096
characters by the API, so screenshots travel in the job log instead.)

  ci-annotations.py failures            failing instrumentation tests, one error each plus a summary
  ci-annotations.py measurements FILE   the text of a measurements file as one notice
"""
import glob
import sys
import xml.etree.ElementTree as ET

MAX_PER_STEP = 10
MAX_MESSAGE = 60_000


def escape_data(text):
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def escape_property(text):
    return escape_data(text).replace(":", "%3A").replace(",", "%2C")


def emit(level, title, message):
    print(f"::{level} title={escape_property(title)}::{escape_data(message[:MAX_MESSAGE])}")


def failing_tests():
    patterns = [
        "engine-host/build/outputs/androidTest-results/**/*.xml",
        "data-persistence/build/outputs/androidTest-results/**/*.xml",
        "app/build/outputs/androidTest-results/**/*.xml",
    ]
    found = []
    for pattern in patterns:
        for path in sorted(glob.glob(pattern, recursive=True)):
            try:
                root = ET.parse(path).getroot()
            except ET.ParseError:
                continue
            for case in root.iter("testcase"):
                bad = case.find("failure")
                if bad is None:
                    bad = case.find("error")
                if bad is None:
                    continue
                name = f"{case.get('classname', '?').rsplit('.', 1)[-1]}#{case.get('name', '?')}"
                detail = (bad.get("message") or bad.text or "").strip().splitlines()
                found.append((name, " / ".join(line.strip() for line in detail[:6])))
    return found


def cmd_failures():
    found = failing_tests()
    if not found:
        emit("notice", "Instrumentation", "no failing tests in the collected reports")
        return
    emit("error", f"Failing instrumentation tests ({len(found)})", "\n".join(f"{n}: {m[:300]}" for n, m in found))
    for name, message in found[: MAX_PER_STEP - 1]:
        emit("error", name, message)


def cmd_measurements(path):
    try:
        text = open(path, encoding="utf-8").read()
    except OSError:
        emit("warning", "Measurements", f"{path} was not written")
        return
    emit("notice", "Measurements", text)


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 2
    command = argv[1]
    if command == "failures":
        cmd_failures()
    elif command == "measurements" and len(argv) >= 3:
        cmd_measurements(argv[2])
    else:
        print(__doc__)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))

#!/usr/bin/env python3
"""Exercise the demo UI only, on an emulator. Preserves demo data unless an explicit reset is requested."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("--serial", required=True)
parser.add_argument("--reset-demo-data", action="store_true")
args = parser.parse_args()
package = "dev.lua.seatpreset.demo"
sdk = Path(os.environ.get("ANDROID_SDK_ROOT", str(Path.home() / "Library/Android/sdk")))
adb = [str(sdk / "platform-tools/adb"), "-s", args.serial]
def command(*values):
    return subprocess.check_output(adb + list(values), text=True).strip()
def shell(*values):
    return command("shell", *values)
assert shell("getprop", "ro.kernel.qemu") == "1", "Refusing to operate on a physical device"
if args.reset_demo_data: shell("pm", "clear", package)
else: shell("am", "force-stop", package)
shell("am", "start", "-f", "0x14000000", "-n", package + "/dev.lua.seatpreset.MainActivity")
def ui():
    shell("uiautomator", "dump", "/sdcard/seat-smoke.xml")
    return ET.fromstring(command("exec-out", "cat", "/sdcard/seat-smoke.xml"))
def tap(label, index=0):
    nodes = [n for n in ui().iter("node") if (n.get("text") or "").casefold() == label.casefold() and (n.get("clickable") == "true" or n.get("resource-id") == "android:id/text1")]
    assert len(nodes) > index, "Control missing: " + label
    points = list(map(int, re.findall(r"[0-9]+", nodes[index].get("bounds"))))
    shell("input", "tap", str((points[0] + points[2]) // 2), str((points[1] + points[3]) // 2))
    time.sleep(.2)
def prefs(name):
    content = shell("run-as", package, "cat", "shared_prefs/" + name + ".xml")
    return {n.get("name"): n.text if n.tag == "string" else n.get("value") for n in ET.fromstring(content)}
def result():
    return prefs("seat-presets-v1")["last-result"]
def position():
    return int(prefs("demo-vehicle")["position"])
def scroll_to(label):
    for start, end in [(900, 350), (350, 900)]:
        for _ in range(12):
            if any((n.get("text") or "").casefold() == label.casefold() for n in ui().iter("node")): return
            shell("input", "swipe", "1905", str(start), "1905", str(end), "200")
    raise AssertionError("Missing " + label)
# Developer mode is a persisted preference, with diagnostic controls hidden by default.
if any(n.get("text") == "Presets" and n.get("clickable") == "true" for n in ui().iter("node")): tap("Presets")
if prefs("seat-presets-v1").get("developer-mode") != "true":
    tap("Settings")
    scroll_to("Developer mode")
    tap("Developer mode")
shell("am", "force-stop", package)
shell("am", "start", "-f", "0x14000000", "-n", package + "/dev.lua.seatpreset.MainActivity")
assert prefs("seat-presets-v1").get("developer-mode") == "true"
if any(n.get("text") == "Presets" and n.get("clickable") == "true" for n in ui().iter("node")): tap("Presets")
# Give the first capture a known, distinct simulation position without resetting data.
tap("Demo controls")
tap("P")
tap("Fresh")
seek = next(n for n in ui().iter("node") if n.get("class") == "android.widget.SeekBar")
x1, y1, x2, y2 = map(int, re.findall(r"[0-9]+", seek.get("bounds")))
shell("input", "tap", str(x1 + (x2 - x1) // 4), str((y1 + y2) // 2))
tap("Presets")
# Save through Edit, away from the daily recall target.
tap("Edit preset", 0)
tap("Save current position…")
previous = prefs("seat-presets-v1").get("presets")
tap("Cancel")
assert prefs("seat-presets-v1").get("presets") == previous
tap("Edit preset", 0)
tap("Save current position…")
tap("Save")
tap("Demo controls")
seek = next(n for n in ui().iter("node") if n.get("class") == "android.widget.SeekBar")
x1, y1, x2, y2 = map(int, re.findall(r"[0-9]+", seek.get("bounds")))
shell("input", "tap", str(x1 + (x2 - x1) * 3 // 4), str((y1 + y2) // 2))
tap("Presets")
tap("Edit preset", 1)
tap("Save current position…")
tap("Save")
stored = json.loads(prefs("seat-presets-v1")["presets"])
first = stored[0]["position"]["coordinates"]["demo-axis"]
second = stored[1]["position"]["coordinates"]["demo-axis"]
assert first != second, "Positions should differ"
tap("Use preset", 0)
assert position() == first and "preset recalled" in result()
tap("Use preset", 1)
assert position() == second and "preset recalled" in result()
def configure(label):
    tap("Demo controls")
    tap(label)
    tap("Presets")
# D and stale readings reject immediately, with no replay when P returns.
configure("D")
tap("Use preset", 0)
assert position() == second and "Park in P" in result()
configure("P")
assert position() == second and "Park in P" in result()
configure("Stale")
tap("Use preset", 0)
assert position() == second and "stale" in result()
configure("Fresh")
configure("Unavailable")
tap("Use preset", 0)
assert position() == second and "unavailable" in result()
configure("Fresh")
configure("Parking brake confirmed")
tap("Use preset", 0)
assert position() == second and "Parking brake" in result()
configure("Parking brake confirmed")
# Abrupt death preserves presets and never recalls on startup.
shell("am", "force-stop", package)
shell("am", "start", "-f", "0x14000000", "-n", package + "/dev.lua.seatpreset.MainActivity")
ui()
assert json.loads(prefs("seat-presets-v1")["presets"]) == stored
assert position() == second and "Parking brake" in result()
tap("Use preset", 0)
assert position() == first
print("PASS: matte UI, edit capture, two positions, D/stale/unavailable/brake rejection, no replay, restart persistence")

#!/usr/bin/env python3
"""Exercise real Settings on an emulator. Never connects to or operates a physical car."""
import argparse
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--serial', required=True)
parser.add_argument('--package', default='dev.lua.seatpreset')
parser.add_argument('--output-dir', default='docs/screenshots/settings-compose-qa')
args = parser.parse_args()
adb_command = ['adb', '-s', args.serial]
output = Path(args.output_dir)
output.mkdir(parents=True, exist_ok=True)

def adb(*values):
    return subprocess.check_output(adb_command + list(values), timeout=25)

assert args.serial.startswith('emulator-') and adb('shell', 'getprop', 'ro.kernel.qemu').strip() == b'1', 'Emulator only'
assert args.package in ('dev.lua.seatpreset', 'dev.lua.seatpreset.demo'), 'Unexpected package'

def ui():
    adb('shell', 'uiautomator', 'dump', '/sdcard/seat-settings-smoke.xml')
    return ET.fromstring(adb('exec-out', 'cat', '/sdcard/seat-settings-smoke.xml'))

def labels(node):
    return (node.get('text', '') + '\n' + node.get('content-desc', '')).strip()

def matches(label, contains=False):
    return [n for n in ui().iter('node') if label.casefold() in labels(n).casefold()] if contains else [n for n in ui().iter('node') if label.casefold() in (n.get('text', '').casefold(), n.get('content-desc', '').casefold())]

def bounds(node):
    return list(map(int, re.findall(r'\d+', node.get('bounds', ''))))

def tap(label, index=0, contains=False):
    nodes = matches(label, contains)
    assert len(nodes) > index, 'Missing control: ' + label
    x1, y1, x2, y2 = bounds(nodes[index])
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
    time.sleep(.25)

def dimensions():
    size = adb('shell', 'wm', 'size').decode().splitlines()[-1]
    return tuple(map(int, re.findall(r'(\d+)x(\d+)', size)[0]))

def scroll(down=True):
    width, height = dimensions()
    # The common scroll surface covers both columns and is below the fixed header.
    x = 64  # Scroll in the card gutter, outside the embedded native panel preview.
    top, bottom = int(height*.34), int(height*.82)
    adb('shell', 'input', 'swipe', str(x), str(bottom if down else top), str(x), str(top if down else bottom), '250')
    time.sleep(.2)

def seek(label, contains=False):
    for _ in range(30):
        nodes=matches(label, contains)
        if nodes:
            _,height=dimensions()
            _,y1,_,y2=bounds(nodes[0])
            if y2>y1 and y1>=180 and y2<=height-24:
                return
            # Use a short correction near viewport edges to avoid oscillating past a row.
            delta=(height-60-y2) if y2>height-24 else (210-y1)
            delta=max(-200,min(200,delta))
            adb('shell','input','swipe','64','500','64',str(500+delta),'250')
        else:
            scroll()
    raise AssertionError('Cannot find ' + label)

def top():
    for _ in range(7):
        scroll(False)

def snapshot(name):
    root = ui()
    (output/(name+'.xml')).write_bytes(ET.tostring(root))
    (output/(name+'.png')).write_bytes(adb('exec-out', 'screencap', '-p'))
    text = '\n'.join(labels(n).lower() for n in root.iter('node'))
    for removed in ('supervised seat movement trial', 'child-lock input trial', 'test shortcuts', 'standard android widget', 'widget diagnostics', 'replay child-lock traces'):
        assert removed not in text, 'Removed UI returned: ' + removed
    print('Captured ' + name, flush=True)

def document():
    raw=adb('exec-out', 'run-as', args.package, 'cat', 'files/presets.json')
    if b'No such file or directory' in raw:
        raise FileNotFoundError('First-use preset document has not been saved')
    return json.loads(raw)

def preference(name, key, default='false'):
    result = subprocess.run(adb_command + ['exec-out', 'run-as', args.package, 'cat', 'shared_prefs/'+name+'.xml'], capture_output=True, timeout=15)
    if b'No such file or directory' in result.stdout:
        return default
    if result.returncode:
        raise RuntimeError('Cannot read emulator preference: ' + name)
    nodes = ET.fromstring(result.stdout)
    node = next((n for n in nodes if n.get('name') == key), None)
    return node.get('value', node.text) if node is not None else default

def rename(value):
    tap('Rename')
    editor = next(n for n in ui().iter('node') if n.get('class') == 'android.widget.EditText')
    x1, y1, x2, y2 = bounds(editor)
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
    adb('shell', 'input', 'keyevent', '123')
    adb('shell', 'input', 'keyevent', *(['67']*48))
    adb('shell', 'input', 'text', value.replace(' ', '%s'))
    keyboard=adb('shell','dumpsys','input_method').decode()
    if 'mInputShown=true' in keyboard:
        adb('shell', 'input', 'keyevent', '4')
    tap('Save')

adb('shell', 'wm', 'size', '1920x1080')
adb('shell', 'wm', 'density', '240')
adb('shell', 'am', 'force-stop', args.package)
adb('shell', 'am', 'start', '-n', args.package+'/dev.lua.seatpreset.MainActivity')
time.sleep(.5)
tap('Settings')
snapshot('01-settings-wide')
# Persistence is verified through the real Android store, without changing any coordinates.
try:
    initial=document()
except FileNotFoundError:
    # A first-use document is lazy. Save its existing label through the real UI.
    tap('Rename')
    original=next(n.get('text') for n in ui().iter('node') if n.get('class')=='android.widget.EditText')
    tap('Cancel')
    rename(original)
    initial=document()
first_id = initial['favourites'][0]
first_name = next(p['name'] for p in initial['presets'] if p['id'] == first_id)
rename('QA Driver')
changed = document()
assert next(p['name'] for p in changed['presets'] if p['id'] == first_id) == 'QA Driver'
for old in initial['presets']:
    new = next(p for p in changed['presets'] if p['id'] == old['id'])
    assert old.get('position') == new.get('position'), 'Rename altered geometry'
rename(first_name)
assert document() == initial, 'Rename restore changed the preset document'
tap('Change preset', contains=True)
snapshot('02-favourite-picker')
tap('Cancel')
assert document() == initial
# Favourite swapping preserves two distinct slots and all geometry.
second_id=initial['favourites'][1]
second_name=next(p['name'] for p in initial['presets'] if p['id']==second_id)
tap('Change preset',contains=True)
tap(second_name)
assert document()['favourites']==[second_id,first_id]
tap('Change preset',contains=True)
tap(first_name)
assert document()==initial
# The visible movement opt-in persists, but these tests never request recall.
original_movement=preference('seat-control','enabled')
seek('Allow seat movement')
tap('Allow seat movement')
if original_movement!='true':tap('Enable')
assert preference('seat-control','enabled')!=original_movement
tap('Allow seat movement')
if original_movement=='true':tap('Enable')
assert preference('seat-control','enabled')==original_movement
seek('Left button')
snapshot('03-child-mapping')
original_left=preference('child-bindings','LEFT',None)
tap('Left button')
snapshot('04-child-picker')
tap(first_name)
assert preference('child-bindings','LEFT')==first_id
original_right=preference('child-bindings','RIGHT',None)
seek('Right button')
tap('Right button')
tap(second_name)
assert preference('child-bindings','RIGHT')==second_id
seek('Left button')
snapshot('03a-two-driver-mappings')
tap('Right button')
right_choice=next((p['name'] for p in initial['presets'] if p['id']==original_right),'Not assigned')
tap(right_choice)
assert preference('child-bindings','RIGHT',None)==original_right
seek('Left button')
tap('Left button')
original_choice=next((p['name'] for p in initial['presets'] if p['id']==original_left),'Not assigned')
tap(original_choice)
assert preference('child-bindings','LEFT',None)==original_left
seek('Check on start')
before = bounds(matches('Check on start')[0])
original_check = preference('seat-presets-v1', 'check-updates')
tap('Check on start')
assert preference('seat-presets-v1', 'check-updates') != original_check
after = bounds(matches('Check on start')[0])
assert abs(before[1]-after[1]) <= 3, 'Setting update lost scroll position'
tap('Check on start')
assert preference('seat-presets-v1', 'check-updates') == original_check
seek('Developer tools')
original_developer = preference('seat-presets-v1', 'developer-mode')
if original_developer != 'true':
    tap('Developer tools')
seek('Diagnostics')
snapshot('05-developer-tools')
if args.package=='dev.lua.seatpreset':
    seek('Set up vehicle readings')
    tap('Set up vehicle readings')
    assert matches('Allow vehicle readings?')
    tap('Cancel')
# Cancelling setup cannot change the owner's movement choice.
assert preference('seat-control','enabled')==original_movement
seek('View setup log')
tap('View setup log')
assert matches('Setup log')
tap('Close')
# Back leaves Settings, rather than finishing the app.
adb('shell', 'input', 'keyevent', '4')
assert matches('Settings'), 'Back did not return to preset page'
foreground = adb('shell', 'dumpsys', 'activity', 'activities').decode()
assert args.package+'/dev.lua.seatpreset.MainActivity' in foreground or args.package+'/.MainActivity' in foreground
# Recreation and a narrower window keep Settings reachable.
tap('Settings')
adb('shell', 'wm', 'size', '1440x1080')
time.sleep(.6)
assert matches('Presets'), 'Configuration change lost Settings'
top()
snapshot('06-settings-narrow')
seek('Left button')
snapshot('07-child-narrow')
if original_developer != 'true':
    seek('Developer tools')
    tap('Developer tools')
adb('shell', 'wm', 'size', '1920x1080')
time.sleep(.5)
assert document() == initial, 'UI test changed saved coordinates or favourites'
print('PASS: rename/favourite/child-binding and movement-opt-in persistence, unchanged geometry, dialogs, scroll retention, settings Back, and window recreation', flush=True)

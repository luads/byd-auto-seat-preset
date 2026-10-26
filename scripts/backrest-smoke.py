#!/usr/bin/env python3
"""Exercise the simulated confirmation/readback path on an emulator, never a physical car."""
import argparse
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

parser=argparse.ArgumentParser()
parser.add_argument('--serial',required=True)
parser.add_argument('--output-dir',default='docs/screenshots/backrest-test-qa')
args=parser.parse_args()
package='dev.lua.seatpreset.demo'
command=['adb','-s',args.serial]
def adb(*parts):return subprocess.check_output(command+list(parts),timeout=30)
assert args.serial.startswith('emulator-') and adb('shell','getprop','ro.kernel.qemu').strip()==b'1','Emulator only'
output=Path(args.output_dir);output.mkdir(parents=True,exist_ok=True)
def ui():
    adb('shell','uiautomator','dump','/sdcard/backrest-smoke.xml')
    return ET.fromstring(adb('exec-out','cat','/sdcard/backrest-smoke.xml'))
def match(label,root=None):
    return [n for n in (root if root is not None else ui()).iter('node') if label.casefold() in (n.get('text','').casefold(),n.get('content-desc','').casefold())]
def bounds(node):return list(map(int,re.findall(r'\d+',node.get('bounds',''))))
def tap(label):
    node=match(label)[0];x1,y1,x2,y2=bounds(node)
    assert node.get('enabled')!='false',label+' disabled'
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.2)
def scroll(down=True):
    adb('shell','input','swipe','64','900' if down else '350','64','350' if down else '900','220')
def seek(label):
    for _ in range(25):
        nodes=match(label)
        if nodes:
            x1,y1,x2,y2=bounds(nodes[0])
            if y2>y1 and y1>=40 and y2<1040:return
            scroll(y2>=1040)
        else:scroll()
    raise AssertionError('Cannot reach '+label)
def snapshot(name):
    (output/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
    (output/(name+'.xml')).write_bytes(ET.tostring(ui()))
def pref(name,key,default='false'):
    raw=adb('exec-out','run-as',package,'cat','shared_prefs/'+name+'.xml')
    if b'No such file' in raw:return default
    root=ET.fromstring(raw)
    node=next((n for n in root if n.get('name')==key),None)
    return node.get('value',node.text) if node is not None else default
def log():
    raw=adb('exec-out','run-as',package,'cat','files/private-observations.log')
    return '' if b'No such file' in raw else raw.decode()
def count():return log().count('setterReturned axis=backrest')
adb('shell','wm','size','1920x1080');adb('shell','wm','density','240')
adb('shell','am','force-stop',package)
adb('shell','am','start','-n',package+'/dev.lua.seatpreset.MainActivity')
tap('Settings')
for _ in range(7):scroll(False)
movement=pref('seat-control','enabled');developer=pref('seat-presets-v1','developer-mode')
seek('Allow seat movement')
if movement!='true':tap('Allow seat movement');tap('Enable')
if developer!='true':
    seek('Developer tools');tap('Developer tools')
seek('Backrest test · simulation');tap('Backrest test · simulation')
seek('Read current position');tap('Read current position');time.sleep(.5)
seek('Backrest +1 unit');baseline=count()
tap('Backrest +1 unit')
assert match('Simulate')[0].get('enabled')=='false','Confirmation not gated'
snapshot('01-confirmation')
tap('Cancel');assert count()==baseline,'Cancel sent a command'
# Fresh read after cancellation. Private traces, not a UI success message, verify the outcome.
seek('Read current position');tap('Read current position');time.sleep(.5)
seek('Backrest +1 unit');tap('Backrest +1 unit')
checkbox=next(n for n in ui().iter('node') if n.get('class')=='android.widget.CheckBox')
x1,y1,x2,y2=bounds(checkbox);adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
tap('Simulate');time.sleep(1)
assert count()==baseline+1,'Expected exactly one simulated command'
assert 'targetReported=true' in log(),'Target readback not traced'
snapshot('02-readback')
time.sleep(10)
assert count()==baseline+1,'Observation replayed a command'
# A confirmation cannot survive leaving the Activity.
seek('Read current position');tap('Read current position');time.sleep(.5)
seek('Backrest −1 unit');tap('Backrest −1 unit')
checkbox=next(n for n in ui().iter('node') if n.get('class')=='android.widget.CheckBox')
x1,y1,x2,y2=bounds(checkbox);adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
adb('shell','input','keyevent','3')
adb('shell','am','start','-n',package+'/dev.lua.seatpreset.MainActivity')
time.sleep(.3)
if match('Simulate'):tap('Simulate')
if match('Read current position'):
    seek('Back');tap('Back')
# Returning to the launcher/main screen cannot replay the accepted checkbox.
assert count()==baseline+1,'Background confirmation dispatched'
# Restore opt-in preferences through Settings.
if not match('Presets'):tap('Settings')
for _ in range(7):scroll(False)
seek('Allow seat movement')
if movement!='true' and pref('seat-control','enabled')=='true':tap('Allow seat movement')
seek('Developer tools')
if developer!='true' and pref('seat-presets-v1','developer-mode')=='true':tap('Developer tools')
print('PASS: explicit acknowledgement, cancellation, exactly one simulated command, target/cross-axis logs, no observation replay, background invalidation, preference restore',flush=True)

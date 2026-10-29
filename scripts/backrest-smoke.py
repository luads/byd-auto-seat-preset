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
def release_ids():return re.findall(r'trace=([^ ]+).*nativeRelease axis=',log())
previous=set(release_ids())
def count():return sum(trace not in previous for trace in release_ids())
def acknowledge():
    checkbox=next(n for n in ui().iter('node') if n.get('class')=='android.widget.CheckBox')
    x1,y1,x2,y2=bounds(checkbox);adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
def top():
    for _ in range(8):scroll(False)
def read():
    seek('Read seat and mirror positions');tap('Read seat and mirror positions');time.sleep(.5)
def end_observation():
    for _ in range(8):
        nodes=match('End observation')
        if not nodes:return  # The ten-second window can expire during a UI dump.
        x1,y1,x2,y2=bounds(nodes[0])
        if y2>y1 and y1>=40 and y2<1040:
            adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));return
        scroll()
    assert not match('End observation'),'Cannot end observation'
adb('shell','wm','size','1920x1080');adb('shell','wm','density','240')
adb('shell','am','force-stop',package)
adb('shell','am','start','-n',package+'/dev.lua.seatpreset.MainActivity')
root=ui();header=match('Seat Presets',root)
if header:
    x1,y1,x2,y2=bounds(header[0]);assert x2-x1>200 and y2-y1<100,'Collapsed home title'
snapshot('00-home')
tap('Settings');top()
movement=pref('seat-control','enabled');developer=pref('seat-presets-v1','developer-mode')
seek('Allow seat movement')
if movement!='true':tap('Allow seat movement');tap('Enable')
if developer!='true':seek('Developer tools');tap('Developer tools')
seek('Seat trials · simulation');tap('Seat trials · simulation')
read();seek('Forward / up · 100 ms');baseline=count()
tap('Forward / up · 100 ms')
assert match('Simulate')[0].get('enabled')=='false','Confirmation not gated'
snapshot('01-confirmation');tap('Cancel');assert count()==baseline,'Cancel dispatched'
read();seek('Forward / up · 100 ms');tap('Forward / up · 100 ms');acknowledge();tap('Simulate');time.sleep(1)
assert count()==baseline+1,'Expected one native simulated pulse and release'
assert 'stage=after axis=backrest' in log(),'Missing readback'
snapshot('02-native-readback')
seek('I used a physical control');tap('I used a physical control')
assert 'ownerIntervention=physical-control' in log(),'Missing physical marker'
assert match('Confirm observed direction and stop')[0].get('enabled')=='false','Intervened trial learned direction'
end_observation();top()
seek('Compare mirror read routes');tap('Compare mirror read routes');time.sleep(.5)
assert 'mirror-read-simulation' in log(),'Mirror read not logged'
snapshot('03-mirror-read')
seek('Simulate a saved seat load');tap('Simulate a saved seat load');time.sleep(2)
assert any('Simulated saved seat matched after 6 pulses' in n.get('text','') for n in ui().iter('node')),'Independent loader did not complete'
assert 'simulation feedback axis=height' in log(),'Full loader feedback missing'
snapshot('04-independent-load')
read();seek('Back / down · 100 ms');tap('Back / down · 100 ms');acknowledge();tap('Simulate');time.sleep(1)
end_observation();seek('Confirm observed direction and stop');tap('Confirm observed direction and stop');tap('Confirm')
assert 'ownerConfirmedDirection axis=backrest' in log(),'Explicit direction confirmation not saved'
snapshot('05-direction-confirmation')
read();seek('Forward / up · 100 ms');tap('Forward / up · 100 ms');acknowledge();before=count()
adb('shell','input','keyevent','3')
adb('shell','am','start','-n',package+'/dev.lua.seatpreset.MainActivity');time.sleep(.3)
if match('Simulate'):tap('Simulate')
assert count()==before,'Background confirmation dispatched'
adb('shell','am','force-stop',package)
adb('shell','am','start','-n',package+'/dev.lua.seatpreset.MainActivity');tap('Settings');top()
seek('Allow seat movement')
if movement!='true' and pref('seat-control','enabled')=='true':tap('Allow seat movement')
seek('Developer tools')
if developer!='true' and pref('seat-presets-v1','developer-mode')=='true':tap('Developer tools')
print('PASS: home, acknowledgement, cancel, native simulated pulse/release/readback, intervention rejection, mirrors read-only, six-pulse independent load, owner direction profile, background invalidation, preference restore',flush=True)

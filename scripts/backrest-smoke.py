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
def return_ids():return re.findall(r'trace=([^ ]+).*setterReturned method=',log())
# Old entries can rotate out of the bounded private log during a test. Count only this run.
previous_returns=set(return_ids())
def count():return sum(trace not in previous_returns for trace in return_ids())
def acknowledge():
    checkbox=next(n for n in ui().iter('node') if n.get('class')=='android.widget.CheckBox')
    x1,y1,x2,y2=bounds(checkbox);adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
def top():
    for _ in range(9):scroll(False)
def end_observation():
    for _ in range(8):
        root=ui();nodes=match('End observation',root)
        if not nodes:
            assert any(n.get('text','').startswith('Observation ended.') for n in root.iter('node')),'Observation ended without its status'
            return
        x1,y1,x2,y2=bounds(nodes[0])
        if y2>y1 and y1>=40 and y2<1040:
            adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.2);return
        scroll()
    raise AssertionError('Cannot end observation')
def read():
    seek('Read current position');tap('Read current position');time.sleep(.5)
adb('shell','wm','size','1920x1080');adb('shell','wm','density','240')
adb('shell','am','force-stop',package)
adb('shell','am','start','-n',package+'/dev.lua.seatpreset.MainActivity')
# Horizontal header must leave the title room and must not create a tall empty gap.
root=ui();header=match('Seat Presets',root)
if header:
    x1,y1,x2,y2=bounds(header[0]);assert x2-x1>200 and y2-y1<100,'Collapsed home title'
snapshot('00-home')
tap('Settings');top()
movement=pref('seat-control','enabled');developer=pref('seat-presets-v1','developer-mode')
seek('Allow seat movement')
if movement!='true':tap('Allow seat movement');tap('Enable')
if developer!='true':
    seek('Developer tools');tap('Developer tools')
seek('Seat experiments · simulation');tap('Seat experiments · simulation')
read();seek('Try −1 with selected method');baseline=count()
tap('Try −1 with selected method')
assert match('Simulate')[0].get('enabled')=='false','Confirmation not gated'
snapshot('01-confirmation')
tap('Cancel');assert count()==baseline,'Cancel sent a command'
read();seek('Try −1 with selected method');tap('Try −1 with selected method');acknowledge();tap('Simulate');time.sleep(1)
assert count()==baseline+1,'Expected one simulated B command'
assert 'targetReported=true' in log(),'Target readback not traced'
snapshot('02-single-readback')
seek('I used the physical control');tap('I used the physical control');time.sleep(.5)
assert 'ownerIntervention=physical-control' in log(),'Missing intervention marker'
end_observation();top()
seek('C · Bodywork float · seat batch');tap('C · Bodywork float · seat batch')
read();seek('Try +1 with selected method');tap('Try +1 with selected method');acknowledge();tap('Simulate');time.sleep(1)
assert count()==baseline+2,'Expected one simulated C command'
assert 'method=BATCH sdk=NATIVE payload=floatArrayValue axis=horizontal' in log(),'Batch payload not traced'
snapshot('03-batch-readback')
seek('I restored the native preset');tap('I restored the native preset');time.sleep(.5)
assert 'ownerIntervention=native-preset-restored' in log(),'Missing native restoration marker'
end_observation();top()
seek('A · Integer setter · retired');tap('A · Integer setter · retired')
seek('Try −1 with selected method');assert match('Try −1 with selected method')[0].get('enabled')=='false','Retired method enabled'
top();seek('B · Bodywork float · one axis');tap('B · Bodywork float · one axis')
read();seek('Try −1 with selected method');tap('Try −1 with selected method');acknowledge()
adb('shell','input','keyevent','3')
adb('shell','am','start','-n',package+'/dev.lua.seatpreset.MainActivity');time.sleep(.3)
if match('Simulate'):tap('Simulate')
assert count()==baseline+2,'Background confirmation dispatched'
adb('shell','am','force-stop',package)
adb('shell','am','start','-n',package+'/dev.lua.seatpreset.MainActivity');tap('Settings');top()
seek('Allow seat movement')
if movement!='true' and pref('seat-control','enabled')=='true':tap('Allow seat movement')
seek('Developer tools')
if developer!='true' and pref('seat-presets-v1','developer-mode')=='true':tap('Developer tools')
print('PASS: home header, acknowledgement, cancellation, B/C one command each, payload/readback logs, markers, A retired, background invalidation, preference restore',flush=True)

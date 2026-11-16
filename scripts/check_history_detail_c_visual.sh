#!/usr/bin/env bash
set -Eeuo pipefail
[[ "${GITHUB_ACTIONS:-}" == true ]] || { echo 'Disposable GitHub CI only' >&2; exit 1; }
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || { echo 'Emulator required' >&2; exit 1; }
python3 - <<'PY'
import ast,hashlib,json,os,re,subprocess,time
from pathlib import Path
source=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
assert source==os.environ['GITHUB_SHA'],'Dispatch head/source mismatch'
assert not subprocess.check_output(['git','status','--porcelain'],text=True).strip(),'Dirty source cannot be CI evidence'
evidence_run=os.environ['GITHUB_RUN_ID']+'-'+os.environ['GITHUB_RUN_ATTEMPT']
out=Path('build/task-first-evidence/history-detail-c')/source/evidence_run;out.mkdir(parents=True,exist_ok=False)
package='io.openhoyi.mobile.mock'
def adb(*args,check=True,timeout=30):
    return subprocess.run(['adb',*args],text=True,capture_output=True,timeout=timeout,check=check)
def shell(*args):return adb('shell',*args).stdout.strip()
settings=shell('settings','list','system').splitlines()
keys=['font_scale','accelerometer_rotation','user_rotation']
before={k:next((x.split('=',1)[1] for x in settings if x.startswith(k+'=')),None) for k in keys}
wm={k:shell('wm',k) for k in ['size','density']}
night=shell('cmd','uimode','night');night_mode=re.search(r'Night mode: (auto|yes|no|custom)',night).group(1)
global_entries=shell('settings','list','global').splitlines()
global_before={k:next((x.split('=',1)[1] for x in global_entries if x.startswith(k+'=')),None) for k in ['enable_freeform_support','force_resizable_activities']}
# Reuse the exact existing protocol2 verifier, without executing its legacy matrix.
code=Path('scripts/check_extraction_history_visual_migration.sh').read_text().split("<<'VERIFY_PY'\n",1)[1].split('\nVERIFY_PY',1)[0]
functions=[n for n in ast.parse(code).body if isinstance(n,ast.FunctionDef) and n.name=='require_proof']
assert len(functions)==1
exec(compile(ast.Module(body=functions,type_ignores=[]),'existing_protocol2_verifier','exec'))
cases=[('tablet-dark-medium',2560,1600,360,'medium','dark'),('tablet-light-small',2560,1600,360,'small','light'),
 ('landscape-large',1600,1000,200,'large','light'),('portrait-large',1600,2560,360,'large','dark'),
 ('phone-medium',1080,2400,480,'medium','light'),('short-large',1920,1080,480,'large','dark'),('square-large',900,900,320,'large','light')]
manifest={'sourceSha':source,'cases':[],'requestedCases':[c[0] for c in cases],'beforeSettings':before,'beforeWm':wm,'visualAcceptance':'ASTRA_PENDING'}
apk=Path('mobile/build/outputs/apk/mock/mobile-mock.apk');apksha=hashlib.sha256(apk.read_bytes()).hexdigest()
testapk=Path('mobile/build/outputs/apk/androidTest/mock/mobile-mock-androidTest.apk');testsha=hashlib.sha256(testapk.read_bytes()).hexdigest()
manifest.update(apkSha256=apksha,testApkSha256=testsha,evidenceRun=evidence_run,beforeNight=night,beforeGlobal=global_before)
failure=None
try:
    adb('install','-r',str(apk));adb('install','-r','mobile/build/outputs/apk/androidTest/mock/mobile-mock-androidTest.apk')
    for key in global_before:shell('settings','put','global',key,'1')
    # These platform capabilities are read during system startup; only this disposable emulator reboots.
    adb('reboot');adb('wait-for-device',timeout=180)
    deadline=time.monotonic()+180
    while shell('getprop','sys.boot_completed')!='1':
        assert time.monotonic()<deadline,'CI emulator reboot timed out'
        time.sleep(1)
    for key in global_before:assert shell('settings','get','global',key)=='1'
    shell('settings','put','system','font_scale','1.0');shell('settings','put','system','accelerometer_rotation','0');shell('settings','put','system','user_rotation','0')
    assert float(shell('settings','get','system','font_scale'))==1.0
    for index,(name,width,height,density,font,theme) in enumerate(cases):
        shell('wm','size',f'{width}x{height}');shell('wm','density',str(density))
        shell('cmd','uimode','night','yes' if theme=='dark' else 'no');shell('am','force-stop',package)
        probe=subprocess.run(['adb','shell','sh'],input='p=$(pidof io.openhoyi.mobile.mock 2>&1); s=$?; printf "PID_STATUS=%s\\nPID_OUTPUT=%s\\n" "$s" "$p"\n',text=True,capture_output=True,timeout=10)
        assert probe.returncode==0 and probe.stdout.replace('\r\n','\n')=='PID_STATUS=1\nPID_OUTPUT=\n' and not probe.stderr,'Fresh process unconfirmed'
        params=dict(historyDetailC='true',disposableCiEmulator='true',freshProcessVerified='true',sourceSha=source,apkSha=apksha,
            historyCase=name,evidenceRun=evidence_run,testApkSha=testsha,expectedWidth=str(width),expectedHeight=str(height),expectedDensity=str(density),appFontSize=font,sampleTheme=theme,
            checkDetails=str(index==0).lower(),checkSpecial=str(name=='phone-medium').lower(),taskResize=str(name=='square-large').lower())
        command=['shell','am','instrument','-w','-r']
        for key,value in params.items():command+=['-e',key,value]
        command+=['io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation']
        result=adb(*command,check=False,timeout=600)
        (out/f'{name}-instrumentation.txt').write_text(result.stdout+'\nSTDERR:\n'+result.stderr)
        pulled=adb('pull',f'/sdcard/Android/data/{package}/files/history-detail-c/{source}/{evidence_run}/{name}',str(out/name),check=False)
        assert pulled.returncode==0,'Missing actual native evidence'
        path=out/name/'manifest.json';assert path.is_file(),name
        actual=json.loads(path.read_text());manifest['cases'].append(actual)
        assert actual['actualApkSha256']==apksha and actual['actualTestApkSha256']==testsha and actual['evidenceRun']==evidence_run
        for relative,digest in actual['fileSha256'].items():
            item=Path(relative);assert not item.is_absolute() and '..' not in item.parts
            assert hashlib.sha256((out/name/item).read_bytes()).hexdigest()==digest,(name,relative)
        proofs=list((out/name).rglob('window-frame-sync.json'));assert proofs,'No native frame evidence'
        for proofpath in proofs:
            proof=require_proof(proofpath)
            target=proof['attempts'][proof['acceptedAttempt']-1]
            geometry=proofpath.with_name('geometry.json')
            assert geometry.is_file() and json.loads(geometry.read_text())==target['geometryBefore']['target']==target['geometryAfter']['target']
            for artifact in [proofpath,geometry,proofpath.with_name('window.png')]:
                assert str(artifact.relative_to(out/name)) in actual['fileSha256'],artifact

        assert result.returncode==0 and 'HISTORY_DETAIL_C_CAPTURED' in result.stdout and 'INSTRUMENTATION_CODE: -1' in result.stdout,result.stdout[-4000:]
        assert actual['sourceSha']==source and actual['apkSha256']==apksha and actual['overallStatus']=='PASS_MECHANICAL_ONLY'
        main=actual['main'];assert main['actualAppFont']==font and main['densityDpi']==density
        assert main['windowBoundsPx'][2:]==[width,height] and main['windowBoundsPx'][:2]==[0,0]
        assert abs(main['actualAppFontScale']-{'small':.9,'medium':1,'large':1.1}[font])<1e-6
        if index==0:
            details=out/name/'main/details'
            ranges=json.loads((details/'standalone-metric-ranges.json').read_text())
            tapped=[x for x in ranges['ranges'] if x.get('input',{}).get('method')=='coordinate']
            assert ranges['completed'] and len(tapped)==1
            entry=tapped[0];event=entry['input'];assert entry['metric']==event['metric']=='PRESSURE'
            assert event['source']==0x1002 and event['downAccepted'] and event['upAccepted']
            assert event['dialogClosed'] and event['dataUnchangedAfterClose'] and event['selectionPreservedAfterClose']
            before=require_proof(details/event['preTapProof'])
            before_target=before['attempts'][before['acceptedAttempt']-1]['geometryBefore']['target']
            hit=next(c for c in before_target['standaloneHeader']['controls'] if c['semanticId']=='chart-metric:PRESSURE')
            assert hit['widthDp']>=48 and hit['heightDp']>=48 and hit['visibility']['complete']
            assert event['rectPx']==hit['visibility']['rectPx']
            left,top,right,bottom=event['rectPx']
            assert event['xPx']==(left+right)/2 and event['yPx']==(top+bottom)/2
            dialog=require_proof(details/entry['proof'])
            target=dialog['attempts'][dialog['acceptedAttempt']-1]['geometryBefore']['target']
            for key in ['method','metric','preTapProof','rectPx','xPx','yPx','source','downAccepted','upAccepted']:
                assert target['input'][key]==event[key]
            assert target['metric']=='PRESSURE' and target['rangeText']==target['expectedRange']==hit['contentDescription'].split('; ',1)[1].split(';',1)[0]
            assert target['range']['complete'] and 'bar' in target['rangeText']
            reach=json.loads((details/'standalone-header-reachability.json').read_text())
            assert reach['beforeState']==reach['afterState']
        if name=='phone-medium':assert {'unknown','empty'}<=actual.keys()
        if name=='square-large':assert actual['taskResize']['status']=='PASS_REAL_TASK_RESIZE'
    assert [x['case'] for x in manifest['cases']]==manifest['requestedCases'],'Missing or duplicate case evidence'
    manifest['status']='PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE'
except BaseException as error:
    failure=error;manifest['status']='FAIL_OR_BLOCKED';manifest['failure']=repr(error)
finally:
    restore_errors=[]
    for key,value in before.items():
        try:
            if value is None:shell('settings','delete','system',key)
            else:shell('settings','put','system',key,value)
        except BaseException as error:restore_errors.append(repr(error))
    for key,text in wm.items():
        try:
            match=re.search(r'Override '+key+r':\s*(\S+)',text,re.I)
            shell('wm',key,match.group(1) if match else 'reset')
        except BaseException as error:restore_errors.append(repr(error))
    adb('shell','am','force-stop',package,check=False)
    for key,value in global_before.items():
        try:
            if value is None:shell('settings','delete','global',key)
            else:shell('settings','put','global',key,value)
        except BaseException as error:restore_errors.append(repr(error))
    try:
        shell('cmd','uimode','night',night_mode)
        after_entries=shell('settings','list','system').splitlines()
        after={k:next((x.split('=',1)[1] for x in after_entries if x.startswith(k+'=')),None) for k in keys}
        after_global=shell('settings','list','global').splitlines()
        actual_global={k:next((x.split('=',1)[1] for x in after_global if x.startswith(k+'=')),None) for k in global_before}
        restored_wm={k:shell('wm',k) for k in wm}
        restored_night=shell('cmd','uimode','night')
        manifest.update(afterSettings=after,afterGlobal=actual_global,afterWm=restored_wm,afterNight=restored_night)
        assert after==before and actual_global==global_before and restored_wm==wm and restored_night==night,'CI state restoration readback mismatch'
    except BaseException as error:restore_errors.append(repr(error))
    manifest['restoreErrors']=restore_errors
    if restore_errors:manifest['status']='FAIL_RESTORE'
    (out/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    if restore_errors and failure is None:failure=RuntimeError(restore_errors)
if failure:raise failure
print('HISTORY_C_SEVEN_REAL_CASES_PASSED visualAcceptance=ASTRA_PENDING')
PY

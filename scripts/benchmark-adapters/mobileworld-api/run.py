"""One original MobileWorld Mastodon task through phone Pi and an owned API backend."""
import argparse
import ast
import hashlib
import json
import os
import pathlib
import shlex
import subprocess
import sys
import time
from judge import judge

p=argparse.ArgumentParser()
for name in ['source','ios-source','runtime-root','output','compose','ssh-target','ssh-control','adb','serial','task']:p.add_argument('--'+name,required=True)
p.add_argument('--backend-check',action='store_true')
a=p.parse_args();source=pathlib.Path(a.source);out=pathlib.Path(a.output);out.mkdir(parents=True,exist_ok=True,mode=0o700)
compose=pathlib.PurePosixPath(a.compose)
if not compose.parent.name.startswith('omnibot-mw-'):raise ValueError('Use a dedicated omnibot-mw-* compose project')
ssh=['ssh','-S',a.ssh_control,a.ssh_target];docker='docker compose -f '+shlex.quote(str(compose));baseline=shlex.quote(str(compose.parent/'pi-baseline.sql'))

def reset():
    script='set -eu\n'+docker+' stop web >/dev/null\n'+docker+' exec -T db dropdb -U postgres --if-exists --force mastodon\n'+docker+' exec -T db createdb -U postgres -O postgres mastodon\n'+docker+' exec -T db psql -X -U postgres -d mastodon -v ON_ERROR_STOP=1 < '+baseline+' >/dev/null\n'+docker+' exec -T redis redis-cli FLUSHALL >/dev/null\n'+docker+' start web >/dev/null\n'
    subprocess.run(ssh+[script],check=True,capture_output=True,timeout=90)
    # The API can take a moment to boot after its database is restored.
    for attempt in range(15):
        response=subprocess.run(ssh+[docker+' exec -T web curl -fsS -H "Host: mastodon.local" -H "X-Forwarded-Proto: https" http://127.0.0.1:3000/api/v1/instance'],capture_output=True,timeout=10)
        if response.returncode==0:return
        time.sleep(1)
    raise RuntimeError('Owned backend did not become healthy after reset')

def state_hash():
    dump=subprocess.run(ssh+[docker+' exec -T db pg_dump -U postgres --data-only mastodon'],capture_output=True,check=True,timeout=30)
    raw=b'\n'.join(line for line in dump.stdout.splitlines() if not line.startswith((b'\\restrict ',b'\\unrestrict ')))
    # Ignore only pg_dump randomized psql restrict keys; retain every data row and sequence value.
    return hashlib.sha256(raw).hexdigest()

path=source/'src/mobile_world/tasks/definitions/mastodon'/a.task
parsed=ast.parse(path.read_text());cls=next(n for n in parsed.body if isinstance(n,ast.ClassDef));goal=next(ast.literal_eval(n.value) for n in cls.body if isinstance(n,ast.Assign) and any(isinstance(v,ast.Name) and v.id=='goal' for v in n.targets))
case='mw-'+path.stem.removeprefix('mastodon_').replace('_','-');work=out/(case+'-input');work.mkdir(exist_ok=True,mode=0o700);fixture=work/'fixture';fixture.mkdir(exist_ok=True)
(fixture/'mastodon.json').write_text(json.dumps({'relay':'http://127.0.0.1:18301','scope':'isolated original MobileWorld Mastodon; account test; external federation disabled'}))
(work/'task.json').write_text(json.dumps({'name':case,'goal':goal,'apps':['mastodon'],'rubric':[],'sourceCommit':'aa2ea47052dd15729bfca1f27fa6aca4630607a9','classification':'MobileWorld original isolated backend, phone Pi REST API adaptation, source SQL judge; not official GUI score'}))
reset();before_hash=state_hash();baseline_score=judge(source,a.task,ssh,str(compose))
if a.backend_check:
    reset();after=state_hash();print(json.dumps({'baselineScore':baseline_score,'resetHashStable':before_hash==after}));sys.exit(0 if before_hash==after else 1)
before={x.name for x in out.glob('ios-android-*')};report={'sourceTask':cls.name,'goal':goal,'taskSourceSha256':hashlib.sha256(path.read_bytes()).hexdigest(),'baselineScore':baseline_score,'baselineHash':before_hash,'officialBenchmarkScore':None}
try:
    subprocess.run([a.adb,'-s',a.serial,'reverse','tcp:18301','tcp:18301'],check=True,capture_output=True)
    adapter=pathlib.Path(__file__).resolve().parent.parent/'android-workspace/run.py'
    command=[sys.executable,str(adapter),'--case',case,'--task-file',str(work/'task.json'),'--fixture-dir',str(fixture),'--runtime-root',a.runtime_root,'--ios-source',a.ios_source,'--output',a.output,'--adb',a.adb,'--serial',a.serial]
    run=subprocess.run(command,env=os.environ,capture_output=True,text=True)
    report['runnerExitCode']=run.returncode
    report['artifacts']=[x.name for x in out.glob('ios-android-*') if x.name not in before]
    report['sourceJudge']=judge(source,a.task,ssh,str(compose));report['afterHash']=state_hash()
    # Keep transport diagnostics private beside the run, not in published reports.
    (work/'runner.log').write_text(run.stdout+'\n'+run.stderr)
finally:
    # Preserve execution and judgment even when cleanup transport fails.
    report['backendRestored']=False
    (work/'report.json').write_text(json.dumps(report,indent=2)+'\n')
    for artifact in report.get('artifacts',[]):
        (out/artifact/'backend-report.json').write_text(json.dumps(report,indent=2)+'\n')
    subprocess.run([a.adb,'-s',a.serial,'reverse','--remove','tcp:18301'],capture_output=True)
    reset();report['restoredHash']=state_hash();report['backendRestored']=report['restoredHash']==before_hash
    (work/'report.json').write_text(json.dumps(report,indent=2)+'\n')
    for artifact in report.get('artifacts',[]):
        (out/artifact/'backend-report.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report),flush=True)

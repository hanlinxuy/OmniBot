import sys
import ast
import hashlib
import argparse
import urllib.request
import urllib.error
import uuid
import json
import os
import pathlib
import selectors
import shlex
import subprocess
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

parser = argparse.ArgumentParser(description='Run source-backed iOSWorld file adaptations through Android phone Pi ACP')
parser.add_argument('--case', required=True)
parser.add_argument('--task-file',type=pathlib.Path)
parser.add_argument('--fixture-dir',type=pathlib.Path)
parser.add_argument('--transport', choices=['mcp','shell'], default='mcp')
parser.add_argument('--runtime-root', type=pathlib.Path, required=True)
parser.add_argument('--ios-source', type=pathlib.Path, required=True)
parser.add_argument('--output', type=pathlib.Path, required=True)
parser.add_argument('--adb', required=True)
parser.add_argument('--serial', required=True)
parser.add_argument('--endpoint', default='http://127.0.0.1:18081/v1/chat/completions')
parser.add_argument('--model', default='qwen3.6-35b-a3b-q4_k_m')
parser.add_argument('--provider',choices=['qwen','deepseek'],default='qwen')
parser.add_argument('--api-key-file',type=pathlib.Path)
args = parser.parse_args()
ROOT = args.output.resolve()
ROOT.mkdir(parents=True, exist_ok=True, mode=0o700)
phone_root = args.runtime_root.resolve()
import fcntl
lock = (phone_root / '.tools/android-regression.lock').open('a')
fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
ADB = args.adb
SERIAL = args.serial
PKG = 'cn.com.omnimind.bot'
BASE = [ADB, '-s', SERIAL]
MODEL = args.model
UPSTREAM = args.endpoint
KEY = args.api_key_file.read_text().strip() if args.api_key_file else 'local'
ADAPTER = pathlib.Path(__file__).resolve().parent
MARKER = 'ios-android-' + args.case + '-' + args.provider + '-' + uuid.uuid4().hex[:10]
RUN = ROOT / MARKER
RUN.mkdir(mode=0o700)
START = time.monotonic()
GUEST = '/tmp/' + MARKER
PREFIX = '/data/user/0/' + PKG
HOST = 'local/alpine/tmp/' + MARKER
PORT = 18897
requests = []


class Provider(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
        requests.append(body)
        if len(requests) > 30:
            self.send_error(429, 'bounded task request budget exceeded')
            return
        body['model'] = MODEL
        if 'max_completion_tokens' not in body and 'max_tokens' not in body:
            body['max_tokens'] = 4096
        if args.provider == 'qwen':
            body['chat_template_kwargs'] = {'enable_thinking': False}
        elif args.provider == 'deepseek':
            body['thinking'] = {'type':'disabled'}
        (RUN / f'request-{len(requests):02}.json').write_text(json.dumps(body, ensure_ascii=False, indent=2))
        req = urllib.request.Request(UPSTREAM, data=json.dumps(body).encode(), headers={'Authorization': 'Bearer ' + KEY, 'Content-Type': 'application/json'})
        try:
            upstream = urllib.request.urlopen(req, timeout=90)
        except urllib.error.HTTPError as error:
            data = error.read()
            (RUN / f'error-{len(requests):02}.txt').write_bytes(data)
            self.send_response(error.code)
            self.end_headers()
            self.wfile.write(data)
            return
        except (urllib.error.URLError, ConnectionError, TimeoutError) as error:
            (RUN / 'provider-failure.json').write_text(json.dumps({'type':type(error).__name__,'classification':'infrastructure_failure'}))
            self.send_error(502, 'Upstream model connection failed; task must not be scored as a model failure')
            return
        self.send_response(upstream.status)
        self.send_header('Content-Type', upstream.headers.get('Content-Type', 'text/event-stream'))
        self.end_headers()
        with upstream, (RUN / f'response-{len(requests):02}.sse').open('wb') as output:
            for line in upstream:
                output.write(line)
                output.flush()
                self.wfile.write(line)
                self.wfile.flush()


import re, io, tarfile, textwrap, fcntl
ios = args.ios_source.resolve()
task = json.loads(args.task_file.read_text()) if args.task_file else next(t for t in json.loads((ios/'tasks.json').read_text()) if t['name'] == args.case)
seed = ios/'iphone/apps/cloudsheets/xproj/CloudSheets/Persistence/SeedDataFactory.swift'
source = seed.read_text()
local_work = RUN/'fixture'; local_work.mkdir()
if args.fixture_dir:
    import shutil
    for p in args.fixture_dir.iterdir():
        if p.is_file(): shutil.copyfile(p,local_work/p.name)
elif args.case == 'cloudsheets-003':
    import openpyxl
    block = source.split('title: "2026 Open House Volunteers",')[1].split('// file_sheet_task_tracker')[0]
    fixtures = dict(re.findall(r'"([A-Z]+[0-9]+)": "([^"\n]*)"', block))
    book = openpyxl.Workbook(); sheet=book.active; sheet.title='Volunteers'
    for address,value in fixtures.items(): sheet[address]=value
    filename='2026 Open House Volunteers.xlsx'; book.save(local_work/filename)
elif args.case == 'clouddocs-004':
    block=source.split('title: "Mobile Launch Plan",')[1].split('"""')[1]
    fixtures=textwrap.dedent(block).strip()+'\n'
    filename='Mobile Launch Plan.txt'; (local_work/filename).write_text(fixtures)
else:
    from fixtures import build_fixture
    build_fixture(ios,local_work,args.case)
filenames=sorted(p.name for p in local_work.iterdir())
# Ship pure Python dependencies and the capability into the run-owned directory.
import openpyxl, et_xmlfile, tzdata
# Validate fixtures/dependencies and model connectivity before creating phone state.
try:
    with urllib.request.urlopen(urllib.request.Request(UPSTREAM.rsplit('/',1)[0].rsplit('/',1)[0]+'/models',headers={'Authorization':'Bearer '+KEY}),timeout=30) as response:
        json.load(response)
except (urllib.error.URLError, ConnectionError, TimeoutError) as error:
    (RUN/'failure.json').write_text(json.dumps({'sourceTask':args.case,'type':type(error).__name__,'classification':'infrastructure_preflight_failure','providerRequests':0}))
    (RUN/'cleanup.json').write_text(json.dumps({'workspaceCreated':False,'workspaceAbsent':True}))
    raise
server = ThreadingHTTPServer(('127.0.0.1', PORT), Provider)
threading.Thread(target=server.serve_forever, daemon=True).start()
subprocess.run(BASE + ['reverse', f'tcp:{PORT}', f'tcp:{PORT}'], check=True, capture_output=True)
config = {'providers': {'omnibot': {'api': 'openai-completions', 'baseUrl': f'http://127.0.0.1:{PORT}/v1', 'models': [{'id': MODEL, 'name': MODEL, 'input': ['text'], 'reasoning': False, 'contextWindow': 32768, 'maxTokens': 4096, 'cost': {'input': 0, 'output': 0, 'cacheRead': 0, 'cacheWrite': 0}}]}}}
subprocess.run(BASE + ['shell', 'run-as', PKG, 'mkdir', '-p', HOST + '/work'], check=True, capture_output=True)
subprocess.run(BASE + ['shell', '-T', 'run-as', PKG, 'sh', '-c', shlex.quote('cat > ' + HOST + '/models.json')], input=json.dumps(config).encode(), check=True, capture_output=True)
archive=io.BytesIO()
with tarfile.open(fileobj=archive, mode='w') as tar:
    for filename in filenames:
        tar.add(local_work/filename,arcname='work/'+filename)
    tar.add(ADAPTER/'workspace.py',arcname='workspace.py')
    tar.add(ADAPTER/'presentations.py',arcname='presentations.py')
    tar.add(ADAPTER/'workspace_mcp.py',arcname='workspace_mcp.py')
    tar.add(ADAPTER/'records.py',arcname='records.py')
    for module in (openpyxl,et_xmlfile,tzdata):
        tar.add(pathlib.Path(module.__file__).parent,arcname=module.__name__,filter=lambda info: None if '__pycache__' in info.name else info)
subprocess.run(BASE+['shell','-T','run-as',PKG,'sh','-c',shlex.quote('tar -xf - -C '+HOST)],input=archive.getvalue(),check=True,capture_output=True)
PROMPT=task['goal']+'\nAndroid adaptation: your owned workspace is '+GUEST+'/work. Use the available system file capability via bash: python3 '+GUEST+'/workspace.py list; python3 '+GUEST+'/workspace.py read FILE; python3 '+GUEST+'/workspace.py set FILE SHEET CELL VALUE; python3 '+GUEST+'/workspace.py append FILE TEXT. Quote arguments with spaces. Also available: python3 '+GUEST+'/workspace.py bullet FILE SLIDE_NUMBER TEXT; python3 '+GUEST+'/workspace.py add-slide FILE TITLE BODY; python3 '+GUEST+'/workspace.py star FILE FILE_ID. One command per call. Spreadsheet files are real XLSX and document text is a plain-text export. Operate only these test files. Read back saved state before reporting completion. No iOS GUI is installed, so do not attempt UI navigation.'
if args.transport == 'mcp':
    PROMPT=task['goal']+'\nEvaluation environment: operate only the run-owned files through the available structured workspace tools. The spreadsheet and slide files are real XLSX/PPTX; documents are text exports. Read the actual data, make the requested changes and confirm saved state. These tools replace source-app navigation for this Android adaptation. For dated source data, the fixture reference date is 2026-03-05. Use source-backed facts; do not invent issue numbers, names, or completed work details. When content is unspecified, keep wording generic. Report only requested results accurately, using structured time/price fields without mental conversion. Changes across artifacts must convey the same concrete plan; avoid dangling tracker cells and placeholder text. When adding spreadsheet rows, ensure existing total formulas include the new rows and read back both the row and the formula. Do not infer the business content of your changes from these environment instructions. Group independent reads in one assistant turn where possible. Use query text and field projections to narrow large collections; do not repeatedly request the same first page with a larger limit. Use aggregation tools for exact totals and frequencies. Follow pagination when the request requires a complete list. Attribute statements to their actual sender; preferences expressed by other senders are not necessarily user preferences. Distinguish past, upcoming and cancelled records using their explicit status and date. If a required source fact is unavailable, report the gap instead of inventing it. Do not substitute a different recipient or communication channel when the requested one is unavailable; complete independent parts and clearly report the blocked step.'
if args.case=='weather-004':
    PROMPT += '\nThis case uses the live NWS forecast snapshot. Use capturedAt and the forecast startTime fields as the date reference instead of the March fixture date. State the weekend dates and temperature units.'
(RUN/'task.json').write_text(json.dumps({'sourceTask':args.case,'sourceCommit':task.get('sourceCommit','c59287b6ec9a9d0d7269613ad873f6aa9db0f42c'),'prompt':PROMPT,'rubric':task['rubric'],'fixtureSeedSha256':hashlib.sha256(seed.read_bytes()).hexdigest() if not args.fixture_dir else None,'fixtureFileSha256':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in local_work.iterdir() if p.is_file()},'adapterSha256':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in ADAPTER.glob('*.py')},'adaptation':task.get('classification','Original seed data in phone-owned files; native file capability replaces iOS GUI; not official score')},indent=2))
apk = subprocess.check_output(BASE + ['shell', 'pm', 'path', PKG], text=True).strip()[8:]
from workspace_mcp import TOOLS, RECORD_TOOLS
selected_tools=shlex.quote(json.dumps(['mcp__workspace__'+name for name in [*TOOLS,*RECORD_TOOLS]] if args.transport=='mcp' else ['bash','read']))
command = f'export OMNIBOT_PI_TOOL_EXECUTION=sequential OMNIBOT_PI_TOOLS_JSON={selected_tools} HOME=/root PATH=/root/.npm-global/bin:$PATH PI_CODING_AGENT_DIR={GUEST} OMNIBOT_PI_MODELS_PATH={GUEST}/models.json OMNIBOT_PI_MODEL={MODEL} OMNIBOT_PI_API_KEY=fixture; exec omnibot-pi-acp'
script = f'''cd {PREFIX}
export PREFIX={PREFIX} LINKER=/system/bin/linker64
export LD_LIBRARY_PATH="$PREFIX/local/lib" PROOT_LOADER={apk[:-9]}/lib/arm64/libproot-loader.so
export PROOT_TMP_DIR="$PREFIX/cache" TMPDIR="$PREFIX/tmp"
export OMNIBOT_TERMINAL_DISTRIBUTION=alpine OMNIBOT_HEADLESS=1 OMNIBOT_DISABLE_PROOT_LINK2SYMLINK=1
export OMNIBOT_DNS_FILE="$PREFIX/local/omnibot-resolv.conf"
exec /system/bin/sh "$PREFIX/local/bin/init-host" /bin/sh -lc {shlex.quote(command)}
'''
stderr = (RUN / 'stderr.txt').open('w')
child = subprocess.Popen(BASE + ['shell', '-T', 'run-as', PKG, 'sh', '-c', shlex.quote(script)], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=stderr, bufsize=0)
sel = selectors.DefaultSelector()
sel.register(child.stdout, selectors.EVENT_READ)
updates = []
buffer = b''
next_id = 0


def send(payload):
    child.stdin.write((json.dumps(payload) + '\n').encode())
    child.stdin.flush()


def rpc(method, params):
    global next_id, buffer
    next_id += 1
    expected = next_id
    send({'jsonrpc': '2.0', 'id': expected, 'method': method, 'params': params})
    deadline = time.monotonic() + 240
    while time.monotonic() < deadline:
        if b'\n' not in buffer:
            if not sel.select(timeout=1):
                if child.poll() is not None:
                    raise RuntimeError('phone adapter exited: ' + str(child.returncode))
                continue
            block = os.read(child.stdout.fileno(), 65536)
            if not block:
                raise RuntimeError('phone adapter stdout closed')
            buffer += block
        while b'\n' in buffer:
            line, buffer = buffer.split(b'\n', 1)
            if not line.strip():
                continue
            msg = json.loads(line)
            if msg.get('id') == expected and ('result' in msg or 'error' in msg):
                if 'error' in msg:
                    raise RuntimeError(json.dumps(msg['error']))
                return msg['result']
            if msg.get('method') == 'session/request_permission':
                choices = msg['params']['options']
                tc=msg['params']['toolCall'];title=tc.get('title','').split(':',1)[0];arg=tc.get('rawInput',{})
                allowed=args.transport=='mcp'
                if title in ['bash','Bash']:
                    sys.path.insert(0,str(ADAPTER))
                    from admission import admit_workspace_command
                    allowed=admit_workspace_command(arg.get('command',''),GUEST+'/workspace.py')
                with (RUN/'permissions.jsonl').open('a') as f:f.write(json.dumps({'tool':title,'arguments':arg,'allowed':allowed})+'\n')
                choice = next(x for x in choices if x['kind'] == ('allow_once' if allowed else 'reject_once'))
                send({'jsonrpc': '2.0', 'id': msg['id'], 'result': {'outcome': {'outcome': 'selected', 'optionId': choice['optionId']}}})
            elif msg.get('method') == 'session/update':
                updates.append(msg['params'])
                with (RUN / 'updates.jsonl').open('a') as f:
                    f.write(json.dumps(msg['params'], ensure_ascii=False) + '\n')
    raise TimeoutError(method)


try:
    initialized = rpc('initialize', {'protocolVersion': 1, 'clientCapabilities': {}, 'clientInfo': {'name': 'phone-pi-preflight', 'version': '1'}})
    session = rpc('session/new', {'cwd': GUEST + '/work', 'mcpServers': [{'name':'workspace','command':'/usr/bin/python3','args':[GUEST+'/workspace_mcp.py'],'env':[]}] if args.transport=='mcp' else []})
    result = rpc('session/prompt', {'sessionId': session['sessionId'], 'prompt': [{'type': 'text', 'text': PROMPT}]})
    rpc('session/close', {'sessionId': session['sessionId']})
    names = [t['function']['name'] for t in requests[0].get('tools', [])]
    saved_dir=RUN/'saved'; saved_dir.mkdir()
    phone_names=subprocess.check_output(BASE+['shell','-T','run-as',PKG,'ls','-1',HOST+'/work'],text=True).splitlines()
    for filename in phone_names:
        if pathlib.PurePosixPath(filename).name!=filename: raise ValueError('Invalid workspace filename')
        saved=subprocess.check_output(BASE+['shell','-T','run-as',PKG,'cat',shlex.quote(HOST+'/work/'+filename)])
        (saved_dir/filename).write_bytes(saved)
    final=''.join(u.get('update',{}).get('content',{}).get('text','') for u in updates if u.get('update',{}).get('sessionUpdate')=='agent_message_chunk')
    if args.fixture_dir:
        acceptance={'externalBackendReviewRequired':True,'semanticReviewRequired':True,'passed':False}
    elif args.case == 'cloudsheets-003':
        sheet=openpyxl.load_workbook(io.BytesIO(saved)).active
        actual={c.coordinate:c.value for row in sheet for c in row if c.value is not None}
        changes={k:(fixtures.get(k),v) for k,v in actual.items() if fixtures.get(k)!=v}
        valid=[k for k,(a,b) in changes.items() if k in ['D2','D3'] and a!='Pending' and b=='Pending']
        name=fixtures['A'+valid[0][1:]] if len(valid)==1 else ''
        acceptance={'exactlyOneEligibleStatusChanged':len(changes)==1 and len(valid)==1,'otherCellsPreserved':all(actual.get(k)==v for k,v in fixtures.items() if k not in valid),'volunteerReported':bool(name) and name in final and 'Pending' in final}
    elif args.case == 'clouddocs-004':
        actual=saved.decode()
        acceptance={'originalBodyPreserved':actual.startswith(fixtures),'Q2NoteAppended':'Q2' in actual[len(fixtures):] and len(actual)>len(fixtures),'savedReported':any(w in final.lower() for w in ['saved','保存'])}
    else:
        from fixtures import verify_fixture
        acceptance=verify_fixture(args.case,local_work,saved_dir,final)
    acceptance['passed']=all(acceptance.values()) and not acceptance.get('semanticReviewRequired',False)
    report={'classification':task.get('classification','iOSWorld Android native-file adaptation; GUI rubric not scored'),'sourceTask':args.case,'provider':args.provider,'transport':args.transport,'model':MODEL,'elapsedSeconds':time.monotonic()-START,'acceptance':acceptance,'initialize':initialized,'promptResult':result,'toolNames':names,'final':final,'providerRequests':len(requests)}
    (RUN / 'result.json').write_text(json.dumps(report, ensure_ascii=False, indent=2))
    print(json.dumps({'stopReason': result.get('stopReason'), 'acceptance': acceptance, 'providerRequests': len(requests), 'artifact': str(RUN)}, ensure_ascii=False), flush=True)
finally:
    if sys.exc_info()[0] is not None:
        (RUN / 'failure.json').write_text(json.dumps({'type':sys.exc_info()[0].__name__,'sourceTask':args.case,'provider':args.provider,'model':MODEL,'message':str(sys.exc_info()[1]),'providerRequests':len(requests)}))
    child.stdin.close()
    try:
        child.wait(timeout=15)
    except subprocess.TimeoutExpired:
        child.terminate()
        child.wait(timeout=10)
    stderr.close()
    work_archive=subprocess.run(BASE+['shell','-T','run-as',PKG,'tar','-cf','-','-C',HOST,'work'],capture_output=True)
    if work_archive.returncode==0: (RUN/'terminal-workspace.tar').write_bytes(work_archive.stdout)
    session_archive=subprocess.run(BASE+['shell','-T','run-as',PKG,'tar','-cf','-','-C',HOST,'sessions'],capture_output=True)
    if session_archive.returncode==0:
        (RUN/'pi-sessions.tar').write_bytes(session_archive.stdout)
    server.shutdown()
    subprocess.run(BASE + ['reverse', '--remove', f'tcp:{PORT}'], capture_output=True)
    subprocess.run(BASE + ['shell', 'run-as', PKG, 'rm', '-rf', HOST], capture_output=True, check=True)

    assert not subprocess.run(BASE + ['shell', 'run-as', PKG, 'test', '!', '-e', HOST], capture_output=True).returncode
    reverse_lines = subprocess.check_output(BASE + ['reverse', '--list'], text=True).splitlines()
    reverse_removed = all(f'tcp:{PORT}' not in line.split() for line in reverse_lines)
    (RUN / 'cleanup.json').write_text(json.dumps({'workspaceAbsent': True, 'reverseRemoved': reverse_removed}))
    assert reverse_removed, 'Owned ADB reverse remains after cleanup'

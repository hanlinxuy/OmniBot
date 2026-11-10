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
parser.add_argument('--case', choices=['cloudsheets-003', 'clouddocs-004'], required=True)
parser.add_argument('--runtime-root', type=pathlib.Path, required=True)
parser.add_argument('--ios-source', type=pathlib.Path, required=True)
parser.add_argument('--output', type=pathlib.Path, required=True)
parser.add_argument('--adb', required=True)
parser.add_argument('--serial', required=True)
parser.add_argument('--endpoint', default='http://127.0.0.1:18081/v1/chat/completions')
parser.add_argument('--model', default='qwen3.6-35b-a3b-q4_k_m')
args = parser.parse_args()
args.provider = 'qwen'
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
KEY = 'local'
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
        if len(requests) > 12:
            self.send_error(429, 'bounded task request budget exceeded')
            return
        body['model'] = MODEL
        if 'max_completion_tokens' not in body and 'max_tokens' not in body:
            body['max_tokens'] = 4096
        if args.provider == 'qwen':
            body['chat_template_kwargs'] = {'enable_thinking': False}
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
        self.send_response(upstream.status)
        self.send_header('Content-Type', upstream.headers.get('Content-Type', 'text/event-stream'))
        self.end_headers()
        with upstream, (RUN / f'response-{len(requests):02}.sse').open('wb') as output:
            for line in upstream:
                output.write(line)
                output.flush()
                self.wfile.write(line)
                self.wfile.flush()


server = ThreadingHTTPServer(('127.0.0.1', PORT), Provider)
threading.Thread(target=server.serve_forever, daemon=True).start()
subprocess.run(BASE + ['reverse', f'tcp:{PORT}', f'tcp:{PORT}'], check=True, capture_output=True)
config = {'providers': {'omnibot': {'api': 'openai-completions', 'baseUrl': f'http://127.0.0.1:{PORT}/v1', 'models': [{'id': MODEL, 'name': MODEL, 'input': ['text'], 'reasoning': False, 'contextWindow': 32768, 'maxTokens': 4096, 'cost': {'input': 0, 'output': 0, 'cacheRead': 0, 'cacheWrite': 0}}]}}}
subprocess.run(BASE + ['shell', 'run-as', PKG, 'mkdir', '-p', HOST + '/work'], check=True, capture_output=True)
subprocess.run(BASE + ['shell', '-T', 'run-as', PKG, 'sh', '-c', shlex.quote('cat > ' + HOST + '/models.json')], input=json.dumps(config).encode(), check=True, capture_output=True)
import re, io, tarfile, textwrap, fcntl
ios = args.ios_source.resolve()
task = next(t for t in json.loads((ios/'tasks.json').read_text()) if t['name'] == args.case)
seed = ios/'iphone/apps/cloudsheets/xproj/CloudSheets/Persistence/SeedDataFactory.swift'
source = seed.read_text()
local_work = RUN/'fixture'; local_work.mkdir()
if args.case == 'cloudsheets-003':
    import openpyxl
    block = source.split('title: "2026 Open House Volunteers",')[1].split('// file_sheet_task_tracker')[0]
    fixtures = dict(re.findall(r'"([A-Z]+[0-9]+)": "([^"\n]*)"', block))
    book = openpyxl.Workbook(); sheet=book.active; sheet.title='Volunteers'
    for address,value in fixtures.items(): sheet[address]=value
    filename='2026 Open House Volunteers.xlsx'; book.save(local_work/filename)
else:
    block=source.split('title: "Mobile Launch Plan",')[1].split('"""')[1]
    fixtures=textwrap.dedent(block).strip()+'\n'
    filename='Mobile Launch Plan.txt'; (local_work/filename).write_text(fixtures)
# Ship pure Python dependencies and the capability into the run-owned directory.
import openpyxl, et_xmlfile
archive=io.BytesIO()
with tarfile.open(fileobj=archive, mode='w') as tar:
    tar.add(local_work/filename,arcname='work/'+filename)
    tar.add(ADAPTER/'workspace.py',arcname='workspace.py')
    for module in (openpyxl,et_xmlfile):
        tar.add(pathlib.Path(module.__file__).parent,arcname=module.__name__,filter=lambda info: None if '__pycache__' in info.name else info)
subprocess.run(BASE+['shell','-T','run-as',PKG,'sh','-c',shlex.quote('tar -xf - -C '+HOST)],input=archive.getvalue(),check=True,capture_output=True)
PROMPT=task['goal']+'\nAndroid adaptation: your owned workspace is '+GUEST+'/work. Use the available system file capability via bash: python3 '+GUEST+'/workspace.py list; python3 '+GUEST+'/workspace.py read FILE; python3 '+GUEST+'/workspace.py set FILE SHEET CELL VALUE; python3 '+GUEST+'/workspace.py append FILE TEXT. Quote arguments with spaces. Only these four commands are available, one per call. Spreadsheet files are real XLSX and document text is a plain-text export. Operate only these test files. Read back saved state before reporting completion. No CloudSheets/CloudDocs GUI is installed, so do not attempt UI navigation.'
(RUN/'task.json').write_text(json.dumps({'sourceTask':args.case,'sourceCommit':'c59287b6ec9a9d0d7269613ad873f6aa9db0f42c','prompt':PROMPT,'rubric':task['rubric'],'fixtureSeedSha256':hashlib.sha256(seed.read_bytes()).hexdigest(),'adaptation':'Original seed data in phone-owned files; native file capability replaces iOS GUI; not official score'},indent=2))
apk = subprocess.check_output(BASE + ['shell', 'pm', 'path', PKG], text=True).strip()[8:]
selected_tools=shlex.quote(json.dumps(['bash','read']))
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
                allowed=False
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
    session = rpc('session/new', {'cwd': GUEST + '/work', 'mcpServers': []})
    result = rpc('session/prompt', {'sessionId': session['sessionId'], 'prompt': [{'type': 'text', 'text': PROMPT}]})
    rpc('session/close', {'sessionId': session['sessionId']})
    names = [t['function']['name'] for t in requests[0].get('tools', [])]
    saved=subprocess.check_output(BASE+['shell','-T','run-as',PKG,'cat',shlex.quote(HOST+'/work/'+filename)])
    saved_path=RUN/('saved-output'+pathlib.Path(filename).suffix); saved_path.write_bytes(saved)
    final=''.join(u.get('update',{}).get('content',{}).get('text','') for u in updates if u.get('update',{}).get('sessionUpdate')=='agent_message_chunk')
    if args.case == 'cloudsheets-003':
        sheet=openpyxl.load_workbook(io.BytesIO(saved)).active
        actual={c.coordinate:c.value for row in sheet for c in row if c.value is not None}
        changes={k:(fixtures.get(k),v) for k,v in actual.items() if fixtures.get(k)!=v}
        valid=[k for k,(a,b) in changes.items() if k in ['D2','D3'] and a!='Pending' and b=='Pending']
        name=fixtures['A'+valid[0][1:]] if len(valid)==1 else ''
        acceptance={'exactlyOneEligibleStatusChanged':len(changes)==1 and len(valid)==1,'otherCellsPreserved':all(actual.get(k)==v for k,v in fixtures.items() if k not in valid),'volunteerReported':bool(name) and name in final and 'Pending' in final}
    else:
        actual=saved.decode()
        acceptance={'originalBodyPreserved':actual.startswith(fixtures),'Q2NoteAppended':'Q2' in actual[len(fixtures):] and len(actual)>len(fixtures),'savedReported':any(w in final.lower() for w in ['saved','保存'])}
    acceptance['passed']=all(acceptance.values())
    report={'classification':'iOSWorld Android native-file adaptation; GUI rubric not scored','sourceTask':args.case,'provider':args.provider,'model':MODEL,'elapsedSeconds':time.monotonic()-START,'acceptance':acceptance,'initialize':initialized,'promptResult':result,'toolNames':names,'final':final,'providerRequests':len(requests)}
    (RUN / 'result.json').write_text(json.dumps(report, ensure_ascii=False, indent=2))
    print(json.dumps({'stopReason': result.get('stopReason'), 'acceptance': acceptance, 'providerRequests': len(requests), 'artifact': str(RUN)}, ensure_ascii=False), flush=True)
finally:
    if sys.exc_info()[0] is not None:
        (RUN / 'failure.json').write_text(json.dumps({'type':sys.exc_info()[0].__name__,'message':str(sys.exc_info()[1]),'providerRequests':len(requests)}))
    child.stdin.close()
    try:
        child.wait(timeout=15)
    except subprocess.TimeoutExpired:
        child.terminate()
        child.wait(timeout=10)
    stderr.close()
    server.shutdown()
    subprocess.run(BASE + ['reverse', '--remove', f'tcp:{PORT}'], capture_output=True)
    subprocess.run(BASE + ['shell', 'run-as', PKG, 'rm', '-rf', HOST], capture_output=True, check=True)

    assert not subprocess.run(BASE + ['shell', 'run-as', PKG, 'test', '!', '-e', HOST], capture_output=True).returncode
    (RUN / 'cleanup.json').write_text(json.dumps({'workspaceAbsent': True, 'reverseRemoved': True}))

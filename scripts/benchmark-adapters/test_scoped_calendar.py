"""Integration control with a local fake native endpoint; never touches a phone."""
import json
from pathlib import Path
import socket
import subprocess
import sys
import tempfile
import threading
import time
import unittest
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class ScopedCalendarTest(unittest.TestCase):
    def test_scope_binding_and_rejected_title_have_no_write(self):
        calls = []

        class Native(BaseHTTPRequestHandler):
            def log_message(self, *_):
                pass

            def do_POST(self):
                body = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
                if body['method'] == 'initialize':
                    result = {'protocolVersion': '2025-03-26', 'capabilities': {}, 'serverInfo': {'name': 'fixture', 'version': '1'}}
                else:
                    calls.append(body['params'])
                    result = {'structuredContent': {'success': True, 'received': body['params']['arguments']}}
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps({'jsonrpc': '2.0', 'id': body['id'], 'result': result}).encode())

        native = ThreadingHTTPServer(('127.0.0.1', 0), Native)
        threading.Thread(target=native.serve_forever, daemon=True).start()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root/'scope.json').write_text(json.dumps({'marker': 'fixture-owned', 'calendarId': '42', 'requiredTitlePrefix': '[AUTO] '}))
            (root/'token').write_text('fixture-token')
            with socket.socket() as reserve:
                reserve.bind(('127.0.0.1', 0))
                port = reserve.getsockname()[1]
            child = subprocess.Popen([sys.executable, str(Path(__file__).with_name('scoped-calendar-mcp.py')),
                                      '--scope', str(root/'scope.json'), '--token-file', str(root/'token'),
                                      '--endpoint', f'http://127.0.0.1:{native.server_port}/mcp', '--port', str(port)],
                                     stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
            try:
                for _ in range(50):
                    self.assertIsNone(child.poll(), 'Adapter exited before accepting a connection')
                    with socket.socket() as probe:
                        if probe.connect_ex(('127.0.0.1', port)) == 0:
                            break
                    time.sleep(.1)
                else:
                    self.fail('Adapter did not start within five seconds')
                request_id = 0

                def rpc(method, params):
                    nonlocal request_id
                    request_id += 1
                    req = urllib.request.Request(f'http://127.0.0.1:{port}/mcp',
                        data=json.dumps({'jsonrpc': '2.0', 'id': request_id, 'method': method, 'params': params}).encode(),
                        headers={'Content-Type': 'application/json', 'Accept': 'application/json, text/event-stream'})
                    with urllib.request.urlopen(req, timeout=5) as response:
                        return json.load(response)['result']

                rpc('initialize', {'protocolVersion': '2025-03-26', 'capabilities': {}, 'clientInfo': {'name': 'test', 'version': '1'}})
                rejected = rpc('tools/call', {'name': 'calendar_event_create', 'arguments': {'title': 'Missing prefix', 'startAt': '2028-02-29', 'endAt': '2028-03-01', 'allDay': True}})
                self.assertEqual('title_prefix_required', (rejected.get('structuredContent') or json.loads(rejected['content'][0]['text']))['code'])
                self.assertEqual([], calls)
                rpc('tools/call', {'name': 'calendar_event_create', 'arguments': {'title': '[AUTO] Requested item', 'startAt': '2028-02-29', 'endAt': '2028-03-01', 'allDay': True}})
                self.assertEqual('fixture-owned [AUTO] Requested item', calls[0]['arguments']['title'])
                self.assertEqual('42', calls[0]['arguments']['calendarId'])
                self.assertEqual('UTC', calls[0]['arguments']['timezone'])
                rpc('tools/call', {'name': 'calendar_event_list', 'arguments': {'startAt': '2028-02-01', 'endAt': '2028-04-01'}})
                self.assertEqual({'calendarId': '42', 'query': 'fixture-owned', 'startAt': '2028-02-01', 'endAt': '2028-04-01'}, calls[1]['arguments'])
                self.assertEqual(2, len(calls))
            finally:
                child.terminate()
                child.communicate(timeout=10)
                native.shutdown()
                native.server_close()


if __name__ == '__main__':
    unittest.main()

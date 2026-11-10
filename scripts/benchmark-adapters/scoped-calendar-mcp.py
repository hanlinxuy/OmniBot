"""Fixture-scoped native calendar and bounded GUI forwarding for phone Pi."""
import argparse,json,pathlib,urllib.request
from mcp.server.mcpserver import MCPServer, Context
from mcp import ClientSession
from mcp.client.streamable_http import streamable_http_client
import httpx2
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--scope',required=True,type=pathlib.Path)
parser.add_argument('--token-file',required=True,type=pathlib.Path)
parser.add_argument('--endpoint',default='http://127.0.0.1:18899/mcp')
parser.add_argument('--port',type=int,default=18900)
args=parser.parse_args()
scope=json.loads(pathlib.Path(args.scope).read_text())
if not isinstance(scope.get('marker'),str) or not scope['marker'] or not isinstance(scope.get('calendarId'),str) or not scope['calendarId']:
    raise ValueError('Scope needs a nonempty unique marker and calendarId')
if not isinstance(scope.get('requiredTitlePrefix',''),str):
    raise ValueError('requiredTitlePrefix must be a string')
headers={'Authorization':'Bearer '+args.token_file.read_text().strip(),'Content-Type':'application/json','Accept':'application/json,text/event-stream'}
request_id=0
gui_calls=0

def native(method,params):
    global request_id
    request_id+=1
    request=urllib.request.Request(args.endpoint,data=json.dumps({'jsonrpc':'2.0','id':request_id,'method':method,'params':params}).encode(),headers=headers)
    with urllib.request.urlopen(request,timeout=180) as response:
        if response.headers.get('Mcp-Session-Id'):headers['Mcp-Session-Id']=response.headers['Mcp-Session-Id']
        raw=response.read().decode()
    if not raw.startswith('{'):raw='\n'.join(x[5:].strip() for x in raw.splitlines() if x.startswith('data:'))
    value=json.loads(raw)
    if 'error' in value:raise RuntimeError(value['error'])
    result=value['result']
    if method=='tools/call':result=result.get('structuredContent') or json.loads(result['content'][0]['text'])
    return result

native('initialize',{'protocolVersion':'2025-03-26','capabilities':{},'clientInfo':{'name':'scoped-fixture-adapter','version':'1'}})
server=MCPServer('scoped-phone',instructions='Calendar tools are bound to this synthetic task fixture. They cannot read personal events. All dates use explicit UTC. GUI sends are limited to one dispatch; inspect its result and never retry an uncertain send.')

@server.tool()
def calendar_event_list(startAt: str, endAt: str) -> dict:
    """Read only this task's calendar events in an explicit ISO-8601 date range. Calendar ID and unique title filter are enforced by the adapter."""
    return native('tools/call',{'name':'calendar_event_list','arguments':{'calendarId':scope['calendarId'],'query':scope['marker'],'startAt':startAt,'endAt':endAt}})

@server.tool()
def calendar_event_create(title: str, startAt: str, endAt: str, description: str = '', allDay: bool = False) -> dict:
    """Create a UTC event in this task's calendar scope. The unique fixture marker is added automatically. Preserve any separate title prefix requested by the task; missing required prefixes are rejected before writing. Verify via a subsequent list call."""
    required=scope.get('requiredTitlePrefix','')
    if required and not title.startswith(required):
        return {'success':False,'code':'title_prefix_required','requiredPrefix':required,'message':'No event was created. Preserve the task-required title prefix and retry with the corrected title.'}
    prefix=scope['marker']+' '
    if not title.startswith(prefix):title=prefix+title
    return native('tools/call',{'name':'calendar_event_create','arguments':{'calendarId':scope['calendarId'],'title':title,'startAt':startAt,'endAt':endAt,'description':description,'allDay':allDay,'timezone':'UTC','reminderMinutes':[]}})

@server.tool()
def context_environment_get() -> dict:
    """Read current device date, time and timezone; does not read personal records."""
    return native('tools/call',{'name':'context_environment_get','arguments':{}})

@server.tool()
async def run_gui(goal: str, ctx: Context, max_steps: int = 30) -> dict:
    """Use the synthetic Mail app com.gmailclone for one bounded send task. Include all intended recipient, subject and body details. Dispatch success is not email acceptance; inspect terminal evidence. One call per task, no uncertain replay."""
    global gui_calls
    if gui_calls:return {'success':False,'code':'gui_already_dispatched','message':'Do not replay an uncertain send; inspect the first call outcome.'}
    if not 1<=max_steps<=30:raise ValueError('max_steps must be 1..30')
    gui_calls+=1
    async def forward_progress(progress, total, message):
        await ctx.report_progress(progress,total,message)
    async with httpx2.AsyncClient(headers={k:v for k,v in headers.items() if k!='Mcp-Session-Id'},timeout=180,trust_env=False) as client:
        async with streamable_http_client(args.endpoint,http_client=client) as (read,write):
            async with ClientSession(read,write) as session:
                await session.initialize()
                (pathlib.Path(args.scope).parent/'scoped-gui-dispatched.json').write_text(json.dumps({'dispatched':True}))
                raw=await session.call_tool('run_gui',{'goal':goal+' Use only the synthetic Mail app com.gmailclone. Task marker: '+scope['marker'],'max_steps':max_steps},read_timeout_seconds=180,progress_callback=forward_progress)
    result=raw.structured_content or json.loads(raw.content[0].text)
    (pathlib.Path(args.scope).parent/'scoped-gui-receipt.json').write_text(json.dumps(result,ensure_ascii=False,indent=2))
    return result

server.run(transport='streamable-http',host='127.0.0.1',port=args.port,stateless_http=True,json_response=True)

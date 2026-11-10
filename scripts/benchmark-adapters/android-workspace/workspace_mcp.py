"""Run-owned phone-local MCP capabilities; no shell interpolation."""
import json
import pathlib
import subprocess
import sys

TOOLS = {
    'list_files': ('list', [], 'List files in the owned workspace.'),
    'read_file': ('read', ['file'], 'Read a document, workbook, presentation or drive metadata.'),
    'set_cell': ('set', ['file', 'sheet', 'cell', 'value'], 'Set one workbook cell and persist it.'),
    'append_text': ('append', ['file', 'text'], 'Append text, including multiple lines, to a document.'),
    'add_bullet': ('bullet', ['file', 'slide', 'text'], 'Append a bullet to an existing slide. Slide numbers start at 1.'),
    'add_slide': ('add-slide', ['file', 'title', 'body'], 'Append a new slide with title and body.'),
    'star_file': ('star', ['file', 'file_id'], 'Star the selected file in drive metadata. Does not alter its modified date.'),
}

RECORD_TOOLS = {
 'create_note': ('Create a new note in an existing source folder. Refuses duplicate titles; no real account changes.', {'file':'string','folder':'string','title':'string','text':'string'}, ['file','folder','title','text']),
 'request_ride': ('Create an isolated CityRide request from an existing route estimate ID. No real dispatch or payment. Read the source estimates to select route and ride type.', {'file':'string','estimate_id':'string'}, ['file','estimate_id']),
 'reserve_table': ('Create an isolated DineSpot reservation from a bookable source slot ID, preserving its date and party size. No external booking or payment.', {'file':'string','slot_id':'string','dining_preference':'string'}, ['file','slot_id']),
 'add_movie_to_wishlist': ('Save a movie to Wishlist in the isolated fixture.', {'file':'string','record_id':'string'}, ['file','record_id']),
 'archive_mail': ('Archive one inbox message in the isolated fixture.', {'file':'string','record_id':'string'}, ['file','record_id']),
 'list_threads': ('List compact conversation metadata, unread counts and latest incoming timestamps. TeamChat uses the current workspace.', {'file':'string'}, ['file']),
 'read_thread': ('Read actual message content in a conversation before replying. Defaults to the latest 10 messages; offset and limit allow history pagination. TeamChat uses the current workspace.', {'file':'string','thread_id':'string','offset':'integer','limit':'integer'}, ['file','thread_id']),
 'send_message': ('Send a message to an existing conversation in the isolated fixture only. No real messages leave this workspace.', {'file':'string','thread_id':'string','text':'string'}, ['file','thread_id','text']),
 'send_direct_message': ('Send a direct message to an existing LockedIn connection, creating a conversation if needed. Isolated fixture only.', {'file':'string','recipient_id':'string','text':'string'}, ['file','recipient_id','text']),
 'save_job': ('Save a job posting in the isolated fixture.', {'file':'string','record_id':'string'}, ['file','record_id']),
 'follow_company': ('Follow a company in the isolated fixture.', {'file':'string','company':'string'}, ['file','company']),
 'append_note_bullet': ('Append a real bullet to a note while preserving its existing text and folder. If the user provides no bug details, use a generic completion bullet rather than inventing an issue.', {'file':'string','folder':'string','title':'string','text':'string'}, ['file','folder','title','text']),
 'search_flights': ('Search flights by airport codes with correctly associated prices and departure/arrival local times. Prefer this over reading the raw itinerary collection. Results sorted by price; report trip type.', {'file':'string','origin':'string','destination':'string','trip_type':'string'}, ['file','origin','destination']),
 'convert_time': ('Convert an ISO timestamp with offset to an IANA time zone using the time zone database, including daylight saving. Do not mentally convert UTC into venue-local time.', {'timestamp':'string','timezone':'string'}, ['timestamp','timezone']),
 'append_note': ('Append content to an existing note in the specified folder. Preserves existing note content.', {'file':'string','folder':'string','title':'string','text':'string'}, ['file','folder','title','text']),
 'query_records': ('Query source app records. Empty collection returns keys; use dot paths for nested collections. Exact filters and sort_by support nested keys. Query is case-insensitive word matching.', {'file':'string','collection':'string','query':'string','filters':'object','sort_by':'string','descending':'boolean','offset':'integer','limit':'integer','fields':'array'}, ['file']),
 'add_to_list': ('Add a restaurant to a saved list in the isolated fixture.', {'file':'string','record_id':'string','list_id':'string'}, ['file','record_id','list_id']),
 'give_kudos': ('Give kudos once to a friend activity in the isolated fixture.', {'file':'string','record_id':'string'}, ['file','record_id']),
 'add_to_cart': ('Add a product to the isolated cart. No purchase or payment is performed.', {'file':'string','record_id':'string','quantity':'integer'}, ['file','record_id','quantity']),
 'log_food': ('Log a food serving in an existing daily log. Use the source fixture date, meal and food ID.', {'file':'string','record_id':'string','date':'string','meal':'string','quantity':'number'}, ['file','record_id','date','meal','quantity']),
}


def main():
    for line in sys.stdin:
        request = json.loads(line)
        if 'id' not in request:
            continue
        method = request['method']
        if method == 'initialize':
            result = {'protocolVersion': request['params']['protocolVersion'], 'capabilities': {'tools': {}}, 'serverInfo': {'name': 'owned-android-workspace', 'version': '1.0'}}
        elif method == 'ping':
            result = {}
        elif method == 'tools/list':
            result = {'tools': [{'name': name, 'description': description, 'inputSchema': {'type': 'object', 'properties': {field: {'type': 'integer' if field == 'slide' else 'string'} for field in fields}, 'required': fields, 'additionalProperties': False}} for name, (_, fields, description) in TOOLS.items()]}
            result['tools'] += [{'name':name,'description':desc,'inputSchema':{'type':'object','properties':{k:({'type':v,'items':{'type':'string'}} if v=='array' else {'type':v,'enum':['oneWay','roundTrip']} if k=='trip_type' else {'type':v}) for k,v in fields.items()},'required':required,'additionalProperties':False}} for name,(desc,fields,required) in RECORD_TOOLS.items()]
        elif method == 'tools/call':
            params = request['params']; name = params['name']; values = params.get('arguments', {})
            if name in RECORD_TOOLS:
                from records import records
                try:
                    content=records(name,values)
                    result={'content':[{'type':'text','text':json.dumps(content,ensure_ascii=False)}]}
                except (ValueError,KeyError,StopIteration,TypeError) as error:
                    result={'isError':True,'content':[{'type':'text','text':type(error).__name__+': '+str(error)}]}
            elif name not in TOOLS or set(values) != set(TOOLS[name][1]):
                result = {'isError': True, 'content': [{'type': 'text', 'text': 'Unknown tool or invalid argument fields'}]}
            else:
                action, fields, _ = TOOLS[name]
                process = subprocess.run([sys.executable, str(pathlib.Path(__file__).with_name('workspace.py')), action, *[str(values[f]) for f in fields]], capture_output=True, text=True)
                result = {'isError': process.returncode != 0, 'content': [{'type': 'text', 'text': process.stdout or process.stderr}]}
        else:
            print(json.dumps({'jsonrpc': '2.0', 'id': request['id'], 'error': {'code': -32601, 'message': 'Method not found'}}), flush=True)
            continue
        print(json.dumps({'jsonrpc': '2.0', 'id': request['id'], 'result': result}), flush=True)


if __name__ == '__main__':
    main()

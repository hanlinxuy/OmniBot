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
    'star_file': ('star', ['file', 'file_id'], 'Star a source Drive file by metadata ID. file must be drive.json, and file_id must be an id from its files collection, not a document filename. Does not alter modified dates.'),
}

RECORD_TOOLS = {
 'mastodon_report_post': ('Report a selected original Mastodon post using its complete visible text as the report comment, copied by the tool without rewriting Unicode punctuation. Use when the user requests the full original post as the reason. Does not block the author; that is a separate action if requested.', {'file':'string','status_id':'string','category':'string'}, ['file','status_id','category']),
 'aggregate_records': ('Compute exact counts and numeric sum/mean/min/max over all source records, optionally grouped by one scalar dot-path field. Avoid estimating frequencies from one page. Filters match query_records.', {'file':'string','collection':'string','group_by':'string','numeric_fields':'array','filters':'object'}, ['file','collection']),
 'mastodon_request': ('Call the isolated original Mastodon REST API. file=mastodon.json. Use GET /api/v1/accounts/verify_credentials for own account; GET /api/v1/accounts/:id/statuses for posts (pinned=true to verify pins); GET /api/v1/timelines/tag/:tag for all posts with a hashtag, without the # prefix; full-text search may not return every tagged post. GET /api/v1/accounts/:id/following and /api/v1/followed_tags for followed accounts/tags. Following accounts are returned newest follow first, ordered by relationship ID; account.created_at is account creation time, not follow time. Preserve returned order across pagination; GET /api/v1/bookmarks, /favourites, /lists, /filters, /featured_tags; GET /api/v2/search with q and type; POST /api/v1/statuses/:id/pin, /unpin, /bookmark, /unbookmark, /favourite, /unfavourite; POST /api/v1/statuses for a post/reply (status and in_reply_to_id); PATCH /api/v1/accounts/update_credentials for profile; GET /api/v1/instance. POST /api/v1/accounts/:id/unfollow or /block, POST /api/v1/tags/:tag/unfollow; POST /api/v1/lists creates a list with title, replies_policy (followed, list, or none) and exclusive (boolean: true hides list members posts from the home/following feed; API field exclusive, UI label Hide these posts from home). PUT /api/v1/lists/:id updates it; POST /api/v1/lists/:id/accounts adds members using account_ids (array of opaque ID strings). POST /api/v1/featured_tags uses name (without #). Edit existing posts with PUT /api/v1/statuses/:id, never delete/recreate as an editing fallback. First GET /api/v1/statuses/:id and /api/v1/statuses/:id/source for original fields/plain text. This server treats omitted status/media_ids/poll fields as empty during updates: preserve the original status text, all media_ids (array of strings), sensitive/spoiler_text and any poll. For attached image ALT use media_attributes=[{id: opaque media ID, description: updated ALT}] together with the complete media_ids array; top-level description is ignored. PUT /api/v1/media/:id only handles unattached media. Poll updates require poll.options (array of strings), multiple, hide_totals and expires_in (duration in seconds, not expires_at). An expired original cannot retain its expired deadline through this endpoint; supplying a new duration reopens it. Report this constraint rather than claiming preservation or deleting/recreating. Preserve unrelated settings. Read back the same status ID and verify requested changes plus retained text, attachments and poll; HTTP 200 alone is not acceptance. POST /api/v1/reports requires account_id, status_ids (array of strings), category (spam, violation, or other), and comment. When copying content verbatim preserve Unicode punctuation; do not rewrite quotes or apostrophes. GET/POST /api/v2/filters reads/creates filters: title, context (array of home, notifications, public, thread, account), filter_action (warn or hide), expires_in (seconds), keywords_attributes (array of keyword and whole_word). GET /api/v1/mutes lists muted accounts; POST /api/v1/accounts/:id/mute accepts notifications (boolean: hide notifications) and duration (seconds, 0 means indefinite). Read source input files rather than guessing keywords or accounts. Treat all API IDs as strings, never round or convert them to numbers. params contains query parameters for GET, JSON body otherwise. GET pages are capped at 5 records to preserve complete structured results. Follow paginationLink by copying its path/query, never the host. Only fixture account test; no external federation.', {'file':'string','method':'string','path':'string','params':'object'}, ['file','method','path']),
 'place_food_order': ('Create an isolated delivery order from source restaurant/menu/address IDs. items maps menu item IDs to quantities. Source fees and default tip apply. No real payment, order, delivery or external account.', {'file':'string','restaurant_id':'string','address_id':'string','items':'object'}, ['file','restaurant_id','address_id','items']),
 'interact_with_post': ('Like, comment or repost an existing source post in the isolated fixture; comment requires text. No external social interaction.', {'file':'string','record_id':'string','interaction':'string','text':'string'}, ['file','record_id','interaction']),
 'create_document': ('Create a new text document. file must be documents.json (the document metadata store), not the new title or an existing text file. Returns the newly created filename and a fixture-only sharing reference. No public cloud link or real sharing.', {'file':'string','title':'string','text':'string'}, ['file','title','text']),
 'send_mail': ('Write a Sent message to an existing source correspondent in the isolated mail fixture. No real email transmitted.', {'file':'string','recipient':'string','subject':'string','text':'string'}, ['file','recipient','subject','text']),
 'add_movie_to_list': ('Add a source movie to a named custom list, creating that list if absent. Isolated fixture only.', {'file':'string','record_id':'string','list_name':'string'}, ['file','record_id','list_name']),
 'list_channels': ('Discover TeamChat channel names and exact IDs without loading message history. Call read_thread with a returned ID to read messages, then send_message to post.', {'file':'string'}, ['file']),
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
    files={p.name for p in pathlib.Path(__file__).with_name('work').iterdir()}
    allowed={'list_files','read_file','query_records','aggregate_records','convert_time'}
    for extension,names in {'.xlsx':['set_cell'],'.pptx':['add_bullet','add_slide'],'.txt':['append_text']}.items():
        if any(p.endswith(extension) for p in files): allowed.update(names)
    capability_files={
        'mastodon.json':['mastodon_request','mastodon_report_post'], 'documents.json':['create_document'], 'drive.json':['star_file'], 'notes.json':['create_note','append_note','append_note_bullet'],
        'teamchat.json':['list_channels','list_threads','read_thread','send_message'],
        'quickchat.json':['list_threads','read_thread','send_message'],
        'quickbite.json':['place_food_order'], 'lockedin.json':['interact_with_post','list_threads','read_thread','send_message','send_direct_message','save_job','follow_company'],
        'skytrip.json':['search_flights'], 'cityride.json':['request_ride'], 'dinespot.json':['reserve_table'],
        'cinephile.json':['add_movie_to_wishlist','add_movie_to_list'], 'mail.json':['archive_mail','send_mail'],
        'tasterank.json':['add_to_list'], 'trailblaze.json':['give_kudos'],
        'freshcart.json':['add_to_cart'], 'megamart.json':['add_to_cart'], 'caltrack.json':['log_food'],
    }
    for file,names in capability_files.items():
        if file in files: allowed.update(names)
    policy_path = pathlib.Path(__file__).with_name('task-policy.json')
    if policy_path.exists():
        allowed.difference_update(json.loads(policy_path.read_text())['deniedTools'])
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
            result = {'tools': [{'name': name, 'description': description, 'inputSchema': {'type': 'object', 'properties': {field: ({'type': 'string', 'enum': ['drive.json']} if name == 'star_file' and field == 'file' else {'type': 'integer' if field == 'slide' else 'string'}) for field in fields}, 'required': fields, 'additionalProperties': False}} for name, (_, fields, description) in TOOLS.items() if name in allowed]}
            result['tools'] += [{'name':name,'description':desc,'inputSchema':{'type':'object','properties':{k:({'type':'string','enum':['documents.json']} if name=='create_document' and k=='file' else {'type':v,'items':{'type':'string'}} if v=='array' else {'type':v,'enum':['oneWay','roundTrip']} if k=='trip_type' else {'type':v}) for k,v in fields.items()},'required':required,'additionalProperties':False}} for name,(desc,fields,required) in RECORD_TOOLS.items() if name in allowed]
        elif method == 'tools/call':
            params = request['params']; name = params['name']; values = params.get('arguments', {})
            if name not in allowed:
                result={'isError':True,'content':[{'type':'text','text':'Tool unavailable for this workspace'}]}
            elif name in RECORD_TOOLS:
                from records import records
                try:
                    content=records(name,values)
                    result={'isError':isinstance(content,dict) and 'error' in content,'content':[{'type':'text','text':json.dumps(content,ensure_ascii=False)}]}
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

"""Source-backed, isolated app data APIs. Never connects to a real user account."""
import json
import pathlib
import uuid


def records(action, args):
    if action=='convert_time':
        from datetime import datetime
        from zoneinfo import ZoneInfo
        value=datetime.fromisoformat(args['timestamp'].replace('Z','+00:00'))
        if value.tzinfo is None: raise ValueError('Timestamp must include an explicit offset or Z')
        local=value.astimezone(ZoneInfo(args['timezone']))
        return {'input':args['timestamp'],'timezone':args['timezone'],'isoLocal':local.isoformat(),'display':local.strftime('%Y-%m-%d %H:%M %Z')}
    root=pathlib.Path(__file__).resolve().parent/'work'
    path=(root/args['file']).resolve()
    if path.parent!=root.resolve() or path.suffix!='.json' or not path.is_file():
        raise ValueError('Use an existing owned JSON fixture')
    data=json.loads(path.read_text())
    if action=='search_flights' and path.name=='skytrip.json':
        from datetime import datetime
        from zoneinfo import ZoneInfo
        zones={'SFO':'America/Los_Angeles','JFK':'America/New_York','LAX':'America/Los_Angeles','ATL':'America/New_York','ORD':'America/Chicago','SEA':'America/Los_Angeles','BOS':'America/New_York'}
        kind=args.get('trip_type','')
        if kind:
            kind={'oneway':'oneWay','roundtrip':'roundTrip'}.get(kind.replace('-','').replace(' ','').lower())
            if kind is None: raise ValueError('trip_type must be oneWay or roundTrip; omit it to compare both')
        rows=[]
        for flight in data['itineraries']:
            if flight['origin']['code']!=args['origin'] or flight['destination']['code']!=args['destination']: continue
            if kind and flight['tripType']!=kind: continue
            segments=flight['outboundSegments']; start=segments[0];end=segments[-1]
            def local(value,code):
                return datetime.fromisoformat(value.replace('Z','+00:00')).astimezone(ZoneInfo(zones[code])).strftime('%Y-%m-%d %H:%M %Z') if code in zones else value+' (UTC)'
            rows.append({'id':flight['id'],'tripType':flight['tripType'],'cabin':flight['fare']['cabin'],'price':flight['fare']['price'],'currency':flight['fare']['currency'],'departureLocal':local(start['departureTime'],args['origin']),'arrivalLocal':local(end['arrivalTime'],args['destination']),'departureUTC':start['departureTime'],'arrivalUTC':end['arrivalTime'],'durationMinutes':int((datetime.fromisoformat(end['arrivalTime'].replace('Z','+00:00'))-datetime.fromisoformat(start['departureTime'].replace('Z','+00:00'))).total_seconds()/60),'flightNumbers':[s['carrierCode']+s['flightNumber'] for s in segments]})
        rows.sort(key=lambda x:x['price'])
        return {'count':len(rows),'sortedBy':'price ascending','flights':rows,'cheapest':rows[0] if rows else None,'mostExpensive':rows[-1] if rows else None,'note':'One-way and round-trip prices are distinct products; disclose tripType when comparing.'}
    if action in ('list_threads','read_thread') and path.name in ('quickchat.json','teamchat.json','lockedin.json'):
        threads=data['chats'] if path.name=='quickchat.json' else data['conversations'] if path.name=='lockedin.json' else data['workspaces'][0]['dmConversations']+(data['workspaces'][0]['channels'] if action=='read_thread' else [])
        if action=='read_thread':
            thread=next(x for x in threads if x['id']==args['thread_id'])
            limit=min(50,max(1,args.get('limit',10)))
            offset=max(0,args.get('offset',max(0,len(thread['messages'])-limit)))
            messages=thread['messages'][offset:offset+limit]
            return {'thread':{k:v for k,v in thread.items() if k!='messages'},'messages':messages,'offset':offset,'totalMessages':len(thread['messages']),'nextOffset':offset+len(messages) if offset+len(messages)<len(thread['messages']) else None,'note':'Defaults to the latest messages. Read the incoming message before composing a contextual reply.'}
        rows=[]
        for thread in threads:
            messages=thread['messages']; last=messages[-1] if messages else {}
            incoming=[m for m in messages if (m.get('senderId')!=data['workspaces'][0]['currentUserId'] if path.name=='teamchat.json' else m.get('senderID')!=data['currentUserID'] if path.name=='quickchat.json' else not m.get('isFromCurrentUser'))]
            rows.append({'id':thread['id'],'name':thread.get('name',thread.get('title',thread.get('participantName'))),'unreadCount':thread.get('unreadCount',0),'lastTimestamp':last.get('timestamp',last.get('sentAt')),'lastIncomingTimestamp':incoming[-1].get('timestamp',incoming[-1].get('sentAt')) if incoming else None,'messageCount':len(messages)})
        return {'threads':rows,'scope':'current workspace only' if path.name=='teamchat.json' else 'all conversations'}
    if action=='query_records':
        value=data
        for part in args.get('collection','').split('.'):
            if part: value=value[int(part)] if isinstance(value,list) else value[part]
        if isinstance(value,dict):
            return {'keys':{k:len(v) if isinstance(v,(dict,list)) else v for k,v in value.items()},'hint':'Select a collection with a dot path, e.g. state.activities'}
        filters=[]; pending=list(args.get('filters',{}).items())
        while pending:
            key,expected=pending.pop()
            if isinstance(expected,dict): pending.extend((key+'.'+child,value) for child,value in expected.items())
            else: filters.append((key,expected))
        available=set(); pending=[('',value[0])] if value and isinstance(value[0],dict) else []
        while pending:
            prefix,item=pending.pop()
            for key,child in item.items():
                path_key=prefix+key;available.add(path_key)
                if isinstance(child,dict): pending.append((path_key+'.',child))
        invalid=[key for key,_ in filters if available and key not in available]
        if invalid: return {'error':'unknown_filter_field','invalidFields':invalid,'availableFields':sorted(available)}
        found=[]
        for item in value:
            if not all(word.casefold() in json.dumps(item,ensure_ascii=False).casefold() for word in args.get('query','').split()): continue
            match=True
            for key,expected in filters:
                actual=item
                for part in key.split('.'): actual=actual.get(part) if isinstance(actual,dict) else None
                if actual!=expected: match=False
            if match: found.append(item)
        if args.get('sort_by'):
            def sort_value(item):
                for part in args['sort_by'].split('.'): item=item[part]
                return item
            found.sort(key=sort_value,reverse=args.get('descending',False))
        offset=max(0,args.get('offset',0)); limit=min(20,max(1,args.get('limit',5)))
        rows=found[offset:offset+limit]
        if path.name=='ticketbox.json' and args.get('collection')=='events':
            from datetime import datetime
            from zoneinfo import ZoneInfo
            for row in rows:
                if row.get('city')=='San Francisco':
                    row['eventTimeZone']='America/Los_Angeles'
                    row['eventLocalTime']=datetime.fromisoformat(row['date'].replace('Z','+00:00')).astimezone(ZoneInfo(row['eventTimeZone'])).strftime('%Y-%m-%d %H:%M %Z')
        if args.get('fields'):
            rows=[{key:item.get(key) for key in args['fields']} for item in rows]
        bounded=[]; size=0
        for row in rows:
            row_size=len(json.dumps(row,ensure_ascii=False))
            if bounded and size+row_size>12000: break
            bounded.append(row);size+=row_size
        next_offset=offset+len(bounded)
        return {'totalMatches':len(found),'offset':offset,'returnedCount':len(bounded),'nextOffset':next_offset if next_offset<len(found) else None,'effectiveLimit':limit,'availableFields':sorted(available),'items':bounded,'hasMore':next_offset<len(found)}
    if action=='request_ride' and path.name=='cityride.json':
        estimate=next(x for x in data['estimates'] if x['id']==args['estimate_id'])
        state=data['state']; method=state['selectedPaymentMethodId']
        if not any(x['id']==method and x['isAvailable'] for x in state['walletState']['paymentMethods']):
            raise ValueError('Source fixture has no available selected payment method')
        trip={k:estimate[k] for k in ('pickupName','destinationName','rideType','rideTypeId','etaMinutes','estimatedPrice','currency','routeLabel','sourceType')}
        trip.update(id='owned_'+uuid.uuid4().hex,tripStatus='requesting',requestedAt='2026-03-05T12:00:00Z',paymentMethodId=method,pickupNotes='',dropoffNotes='')
        state['trips'].insert(0,trip)
        result={'savedTrip':trip,'scope':'Isolated request record only; no real dispatch, driver lifecycle, or payment.'}
    elif action=='reserve_table' and path.name=='dinespot.json':
        slot=next(s for item in data['availability'] for s in item['availableSlots'] if s['id']==args['slot_id'])
        if not slot['isBookable']: raise ValueError('Selected source slot is not bookable')
        restaurant=next(x for x in data['restaurants'] if x['id']==slot['restaurantID'])
        reservation={'id':'owned_'+uuid.uuid4().hex,'reservationCode':'TEST-'+uuid.uuid4().hex[:8],'restaurantID':restaurant['id'],'date':slot['date'],'partySize':slot['partySize'],'status':'upcoming','diningPreference':args.get('dining_preference',''),'notes':'','sourceType':slot['sourceType'],'policySummary':restaurant['policy']['cancellationPolicy'],'createdAt':'2026-03-05T12:00:00Z','updatedAt':'2026-03-05T12:00:00Z'}
        data['reservations'].append(reservation)
        result={'savedReservation':reservation,'restaurantName':restaurant['name'],'scope':'Isolated reservation only; no external booking, email or payment.'}
    elif action=='add_movie_to_wishlist' and path.name=='cinephile.json':
        item=next(x for x in data['movies'] if str(x['id'])==str(args['record_id']))
        if item['id'] not in data['wishlist']: data['wishlist'].append(item['id'])
        result={'movie':item,'wishlist':data['wishlist']}
    elif action=='archive_mail' and path.name=='mail.json':
        item=next(x for x in data['items'] if x['id']==args['record_id'])
        if item['folder']!='Inbox': raise ValueError('Select an inbox message')
        item['folder']='Archive';result={'archivedMessage':item}
    elif action=='send_message' and path.name in ('quickchat.json','teamchat.json','lockedin.json'):
        text=args['text'].strip()
        if not text: raise ValueError('Message cannot be empty')
        timestamp='2026-03-05T12:00:00Z';mid='owned_'+uuid.uuid4().hex
        if path.name=='quickchat.json':
            thread=next(x for x in data['chats'] if x['id']==args['thread_id'])
            message={'id':mid,'senderID':data['currentUserID'],'content':{'text':text},'sentAt':timestamp,'delivery':'sent','hearted':False,'isStarred':False,'replyToID':None}
        elif path.name=='teamchat.json':
            workspace=data['workspaces'][0];thread=next(x for x in workspace['dmConversations']+workspace['channels'] if x['id']==args['thread_id'])
            message={'id':mid,'senderId':workspace['currentUserId'],'senderDisplayName':workspace['userProfile'].get('displayName','Jordan Avery'),'messageText':text,'timestamp':timestamp,'isUnread':False,'attachments':[],'reactions':[],'threadReplies':[],'replyCount':0,'mentionUserIds':[]}
        else:
            thread=next(x for x in data['conversations'] if x['id']==args['thread_id'])
            message={'id':mid,'senderId':data['profile']['id'],'isFromCurrentUser':True,'content':text,'timestamp':timestamp}
        thread['messages'].append(message);result={'threadId':thread['id'],'savedMessage':message,'isolatedFixtureOnly':True}
    elif action=='send_direct_message' and path.name=='lockedin.json':
        person=next(x for x in data['connections'] if x['id']==args['recipient_id'])
        thread=next((x for x in data['conversations'] if x.get('participantName')==(person['firstName']+' '+person['lastName'])),None)
        if thread is None:
            thread={'id':'owned_'+uuid.uuid4().hex,'participantName':(person['firstName']+' '+person['lastName']),'participantHeadline':person['headline'],'unreadCount':0,'messages':[]};data['conversations'].append(thread)
        message={'id':'owned_'+uuid.uuid4().hex,'senderId':data['profile']['id'],'isFromCurrentUser':True,'content':args['text'],'timestamp':'2026-03-05T12:00:00Z'}
        thread['messages'].append(message);result={'recipient':person,'savedMessage':message,'threadId':thread['id'],'isolatedFixtureOnly':True}
    elif action=='save_job' and path.name=='lockedin.json':
        job=next(x for x in data['jobs'] if x['id']==args['record_id']);job['isSaved']=True;result={'savedJob':job}
    elif action=='follow_company' and path.name=='lockedin.json':
        if not any(x['company']==args['company'] for x in data['jobs']): raise ValueError('Unknown company')
        followed=data.setdefault('followedCompanies',[])
        if args['company'] not in followed: followed.append(args['company'])
        result={'followedCompanies':followed}
    elif action=='create_note' and path.name=='notes.json':
        if args['folder'] not in [x['name'] for x in data['folders']]: raise ValueError('Select an existing note folder')
        if any(x['title']==args['title'] and x['folder']==args['folder'] for x in data['notes']): raise ValueError('Note already exists; append to it instead')
        note={'title':args['title'],'body':args['text'],'folder':args['folder'],'isPinned':False,'isInTrash':False}
        data['notes'].append(note);result={'savedNote':note}
    elif action in ('append_note','append_note_bullet') and path.name=='notes.json':
        item=next(x for x in data['notes'] if x['title']==args['title'] and x['folder']==args['folder'] and not x['isInTrash'])
        item['body']+='\n'+('- '+args['text'].lstrip('-•* ') if action=='append_note_bullet' else args['text']);result={'savedNote':item}
    elif action=='add_to_list' and path.name=='tasterank.json':
        item=next(x for x in data['restaurants'] if x['id']==args['record_id'])
        target=next(x for x in data['lists'] if x['id']==args['list_id'])
        if item['id'] not in target['restaurantIDs']: target['restaurantIDs'].append(item['id'])
        result={'restaurant':item,'list':target}
    elif action=='give_kudos' and path.name=='trailblaze.json':
        item=next(x for x in data['state']['activities'] if x['id']==args['record_id'])
        if item['athleteId']==data['state']['currentUserAthleteId']: raise ValueError('Choose a friend activity')
        if not item['hasCurrentUserKudo']:
            item['hasCurrentUserKudo']=True;item['kudosCount']+=1
        result={'activity':item,'athlete':next(x for x in data['state']['athletes'] if x['id']==item['athleteId'])}
    elif action=='add_to_cart' and path.name in ('megamart.json','freshcart.json'):
        item=next(x for x in data['catalog']['products'] if x['id']==args['record_id'])
        quantity=args['quantity']
        if not isinstance(quantity,int) or not 1<=quantity<=10 or not item['inStock']: raise ValueError('Invalid quantity or product out of stock')
        if path.name=='freshcart.json' and data['state']['selectedStoreID']!=item['storeID']:
            state=data['state'];state['storeCarts'][state['selectedStoreID']]=state['cartItems'];state['cartItems']=state['storeCarts'].get(item['storeID'],[]);state['selectedStoreID']=item['storeID']
        cart=data['state']['cartItems']; old=next((x for x in cart if x['productID']==item['id']),None)
        if old: old['quantity']+=quantity
        elif path.name=='freshcart.json': cart.append({'productID':item['id'],'quantity':quantity,'substitutionPreference':{'type':'bestMatch'},'note':'','addedAt':'2026-03-05T12:00:00Z'})
        else: cart.append({'id':'owned_'+uuid.uuid4().hex,'productID':item['id'],'productName':item['productName'],'quantity':quantity,'unitPrice':item['price']})
        result={'addedProduct':item,'cart':cart,'itemSubtotal':round(sum(x['quantity']*(x['unitPrice'] if 'unitPrice' in x else next(p['price'] for p in data['catalog']['products'] if p['id']==x['productID'])) for x in cart),2),'currency':'USD','checkoutPerformed':False}
    elif action=='log_food' and path.name=='caltrack.json':
        item=next(x for x in data['state']['foodDatabase'] if x['id']==args['record_id'])
        day=next(x for x in data['state']['dailyLogs'] if x['id']==args['date'])
        if args['meal'] not in ('Breakfast','Lunch','Dinner','Snacks') or not 0<args['quantity']<=10: raise ValueError('Invalid meal or serving quantity')
        entry={'id':'owned_'+uuid.uuid4().hex,'date':day['date'],'mealType':args['meal'],'quantity':args['quantity'],'foodItem':item}
        day['mealEntries'].append(entry);result={'savedEntry':entry}
    else:
        raise ValueError('Action is not supported for this fixture')
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2))
    return result

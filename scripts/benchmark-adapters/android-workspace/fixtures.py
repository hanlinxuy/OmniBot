"""Compile upstream Foundation-only seed models and materialize owned file fixtures."""
import hashlib
import json
import pathlib
import subprocess


def build_fixture(ios, output, case):
    import openpyxl
    from pptx import Presentation
    app = case.split('-')[0]
    task_apps=next(t['apps'] for t in json.loads((ios/'tasks.json').read_text()) if t['name']==case)
    if len(task_apps)==1 and app in ('ticketbox','tasterank','trailblaze','megamart','caltrack','skytrip','scorezone','notes','stayfinder','cinephile','mail','freshcart','quickchat','teamchat','lockedin','weather','cityride','dinespot'):
        export=output.parent.parent/'source-exports'/(app+'.json')
        provenance=export.with_name(app+'-provenance.json')
        data=json.loads(export.read_text())
        if app=='caltrack' and not data['state']['foodDatabase']:
            raise ValueError('Food resource missing from source export; fallback calorie entries are invalid fixtures')
        (output/(app+'.json')).write_text(json.dumps(data,ensure_ascii=False,indent=2))
        (output.parent/'seed-provenance.json').write_bytes(provenance.read_bytes())
        return [app+'.json']
    task_apps=next(t['apps'] for t in json.loads((ios/'tasks.json').read_text()) if t['name']==case)
    if any(a in ('mybank','splitpay','clock') for a in task_apps):
        raise ValueError('This isolated adapter excludes payments and requires the native Clock adapter')
    for task_app in task_apps:
        if not task_app.startswith('cloud'):
            export=output.parent.parent/'source-exports'/(task_app+'.json')
            (output/(task_app+'.json')).write_bytes(export.read_bytes())
    folder = ios / 'iphone/apps/cloudsheets/xproj/CloudSheets'
    sources = [folder / p for p in ['Models/WorkspaceModels.swift', 'Utilities/String+Slug.swift', 'Persistence/SeedDataFactory.swift']]
    swift = output.parent / 'export.swift'
    swift.write_text('\n'.join(p.read_text() for p in sources) + '\nlet e = JSONEncoder(); e.dateEncodingStrategy = .iso8601; print(String(data: try e.encode(SeedDataFactory.makeSeededData()), encoding: .utf8)!)\n')
    data = json.loads(subprocess.check_output(['swift', str(swift)], text=True))
    (output.parent / 'seed-provenance.json').write_text(json.dumps({str(p.relative_to(ios)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}, indent=2))
    selection = {
        'cloudslides-002': {'presentations': ['[Draft] Autonomous Agents']},
        'multi-013': {'documents': ['Q2 Roadmap'], 'presentations': ['Conference Practice Deck'], 'spreadsheets': ['Bug Triage Log']},
        'clouddrive-001': {},
    }.get(case,{kind:[item['title'] for item in data[kind]] for kind,app_name in [('documents','clouddocs'),('presentations','cloudslides'),('spreadsheets','cloudsheets')] if app_name in task_apps})
    for kind, titles in selection.items():
        for title in titles:
            item = next(x for x in data[kind] if x['title'] == title)
            filename=title.replace('/', '／')
            if kind == 'documents':
                # Blocks preserve the exact source paragraphs, unlike the encoded body field.
                (output / (filename+'.txt')).write_text('\n'.join(x['text'] for x in item['blocks'])+'\n')
            elif kind == 'spreadsheets':
                book = openpyxl.Workbook(); book.remove(book.active)
                for sheet in item['sheets']:
                    target = book.create_sheet(sheet['name'])
                    for cell in sheet['cells']:
                        target[cell['address']] = cell['rawValue']
                book.save(output / (filename+'.xlsx'))
            else:
                book = Presentation()
                for slide in item['slides']:
                    target = book.slides.add_slide(book.slide_layouts[1])
                    target.shapes.title.text = slide['title']
                    target.placeholders[1].text = slide['body']
                book.save(output / (filename+'.pptx'))
    if 'clouddrive' in task_apps:
        (output/'drive.json').write_text(json.dumps({'folders': data['folders'], 'files': data['files']}, ensure_ascii=False, indent=2))
    return sorted(p.name for p in output.iterdir())


def verify_fixture(case, original, saved, final):
    import openpyxl
    from pptx import Presentation
    if case == 'cloudslides-002':
        name = '[Draft] Autonomous Agents.pptx'
        before, after = Presentation(original/name), Presentation(saved/name)
        old = [[s.text for s in p.shapes if s.has_text_frame] for p in before.slides]
        new = [[s.text for s in p.shapes if s.has_text_frame] for p in after.slides]
        return {'slideCountPreserved': len(new)==len(old), 'firstSlidePreserved': new[0]==old[0],
                'agendaPreserved': new[1][1].startswith(old[1][1]), 'safetyAdded': 'safety' in new[1][1][len(old[1][1]):].lower(),
                'agendaReported': all(line.lower() in final.lower() for line in new[1][1].splitlines())}
    if case == 'multi-013':
        name='Q2 Roadmap.txt'; old=(original/name).read_text(); new=(saved/name).read_text(); addition=new[len(old):]
        before=Presentation(original/'Conference Practice Deck.pptx'); after=Presentation(saved/'Conference Practice Deck.pptx')
        old_slides=[[s.text for s in p.shapes if s.has_text_frame] for p in before.slides]
        new_slides=[[s.text for s in p.shapes if s.has_text_frame] for p in after.slides]
        old_book=openpyxl.load_workbook(original/'Bug Triage Log.xlsx'); new_book=openpyxl.load_workbook(saved/'Bug Triage Log.xlsx')
        old_cells={s.title+':'+c.coordinate:c.value for s in old_book for row in s for c in row if c.value is not None}
        new_cells={s.title+':'+c.coordinate:c.value for s in new_book for row in s for c in row if c.value is not None}
        changes={k:(old_cells.get(k),v) for k,v in new_cells.items() if old_cells.get(k)!=v}
        (saved/'semantic-review.json').write_text(json.dumps({'newDocumentSection':addition,'newSlides':new_slides[len(old_slides):],'changedCells':changes},indent=2))
        return {'originalDocumentPreserved':new.startswith(old),'sectionAdded':len(addition.strip())>20,
                'originalSlidesPreserved':new_slides[:len(old_slides)]==old_slides,'oneSlideAdded':len(new_slides)==len(old_slides)+1,
                'sheetChanged':bool(changes),'originalCellsNotDeleted':set(old_cells)<=set(new_cells),
                'semanticReviewRequired':True}
    if not case.startswith(('mem-','multi-')) and case not in ('lockedin-001','lockedin-002') and case.split('-')[0] in ('ticketbox','tasterank','trailblaze','megamart','caltrack','skytrip','scorezone','notes','stayfinder','cinephile','mail','freshcart','quickchat','teamchat','lockedin','weather','cityride','dinespot'):
        import copy,re
        app=case.split('-')[0]; before=json.loads((original/(app+'.json')).read_text()); after=json.loads((saved/(app+'.json')).read_text()); expected=copy.deepcopy(before)
        if app in ('weather','cityride','dinespot','cinephile','mail','freshcart','quickchat','teamchat','lockedin','weather','cityride','dinespot'):
            if app=='cinephile':
                movie=next(x for x in before['movies'] if x['title']=='The Godfather');expected['wishlist'].append(movie['id'])
                return {'wishlistOnlyChanged':after==expected,'yearReported':movie['release_date'][:4] in final,'ratingReported':str(movie['vote_average']) in final}
            if app=='mail':
                changes=[(x,y) for x,y in zip(before['items'],after['items']) if x!=y]
                if len(changes)!=1:return {'exactlyOneMailChanged':False}
                old,new=changes[0];next(x for x in expected['items'] if x['id']==old['id'])['folder']='Archive'
                return {'quickBiteTransactionFromInbox':old['folder']=='Inbox' and old['category']=='Transactions' and 'quickbite' in json.dumps(old).lower(),'archiveOnlyChanged':after==expected}
            (saved/'semantic-review.json').write_text(json.dumps({'task':case,'final':final},indent=2))
            return {('sourceStateUnchanged' if app=='weather' else 'fixtureChanged'):before==after if app=='weather' else before!=after,'semanticReviewRequired':True}
        if app=='notes':
            old=next(x for x in before['notes'] if x['title']=='Team Standup Notes' and x['folder']=='Work')
            new=next(x for x in after['notes'] if x['title']==old['title'] and x['folder']=='Work')
            next(x for x in expected['notes'] if x['title']==old['title'] and x['folder']=='Work')['body']=new['body']
            extra=new['body'][len(old['body']):]
            return {'originalBodyPreserved':new['body'].startswith(old['body']),'bugFixBullet':bool(re.search(r'[-•].*(?:fix|bug)',extra,re.I)),'onlyTargetNoteChanged':after==expected,'semanticReviewRequired':True}
        if app=='stayfinder':
            (saved/'semantic-review.json').write_text(json.dumps({'task':case,'final':final},indent=2))
            return {'sourceStateUnchanged':before==after,'semanticReviewRequired':True}
        if app=='tasterank':
            target=next(x for x in before['restaurants'] if x['name']=='Tartine Manufactory')
            target_list=next(x for x in expected['lists'] if x['name']=='Want to Try')
            if target['id'] not in target_list['restaurantIDs']: target_list['restaurantIDs'].append(target['id'])
            return {'expectedListMutationOnly':after==expected,'scoreReported':str(target['beliScore']) in final,'cuisineReported':target['cuisine']['name'].lower() in final.lower()}
        if app=='trailblaze':
            changed=[x for x,y in zip(before['state']['activities'],after['state']['activities']) if x!=y]
            if len(changed)!=1: return {'exactlyOneActivityChanged':False}
            target=next(x for x in expected['state']['activities'] if x['id']==changed[0]['id'])
            eligible=not target['hasCurrentUserKudo'] and target['athleteId']!=before['state']['currentUserAthleteId']
            target['hasCurrentUserKudo']=True;target['kudosCount']+=1
            athlete=next(x for x in before['state']['athletes'] if x['id']==target['athleteId'])
            return {'eligibleFriendActivity':eligible,'expectedMutationOnly':after==expected,'friendNamed':athlete['name'].lower() in final.lower(),'typeReported':target['activityType'].lower() in final.lower()}
        if app=='caltrack':
            old_entries=[x['id'] for day in before['state']['dailyLogs'] for x in day['mealEntries']]
            new=[(d,e) for d in after['state']['dailyLogs'] for e in d['mealEntries'] if e['id'] not in old_entries]
            if len(new)!=1:return {'exactlyOneFoodEntry':False}
            day,entry=new[0];food=entry['foodItem']
            next(d for d in expected['state']['dailyLogs'] if d['id']==day['id'])['mealEntries'].append(entry)
            return {'breakfastToday':day['id']=='2026-03-05' and entry['mealType']=='Breakfast','oatmealServing': 'oatmeal' in food['foodName'].lower() and entry['quantity']==1,'expectedMutationOnly':after==expected,'macrosReported':all(str(food[k]) in final for k in ['calories','protein','carbs','fat'])}
        if app=='megamart':
            old={x['productID']:x for x in before['state']['cartItems']}; changed=[x for x in after['state']['cartItems'] if old.get(x['productID'])!=x]
            if len(changed)!=1:return {'exactlyOneCartItemChanged':False}
            item=changed[0];product=next(x for x in before['catalog']['products'] if x['id']==item['productID'])
            expected['state']['cartItems']=after['state']['cartItems']
            return {'oneUnitAdded':item['quantity']-old.get(item['productID'],{}).get('quantity',0)==1,'actualController':'controller' in product['productName'].lower() and not re.search(r'\b(?:station|stand|bundle)\b',product['productName'],re.I),'originalCartPreserved':all(any(y['productID']==k and (y==v or k==item['productID']) for y in after['state']['cartItems']) for k,v in old.items()),'otherStatePreserved':after==expected,'priceReported':str(product['price']) in final,'productReported':product['productName'].lower() in final.lower()}
        if app=='ticketbox':
            venue=next(v for v in before['venues'] if v['name']=='Great American Music Hall')
            event=next(e for e in before['events'] if e['title']=='Ali Wong' and e['venueID']==venue['id'])
            prices=[x['price'] for x in event['listings']]
            (saved/'semantic-review.json').write_text(json.dumps({'expectedDate':event['date'],'expectedVenue':venue['name'],'expectedPriceRange':[min(prices),max(prices)]},indent=2))
            return {'noMutation':before==after,'venueReported':venue['name'].lower() in final.lower(),'priceRangeReported':str(min(prices)) in final and str(max(prices)) in final,'semanticReviewRequired':True}
        (saved/'semantic-review.json').write_text(json.dumps({'task':case,'final':final},indent=2))
        return {'sourceStateUnchanged':before==after,'semanticReviewRequired':True}
    if case.startswith(('multi-','mem-')) or case in ('lockedin-001','lockedin-002'):
        return {'sourceFilesPreserved':all((saved/p.name).is_file() for p in original.iterdir()),'semanticReviewRequired':True}
    before=json.loads((original/'drive.json').read_text()); after=json.loads((saved/'drive.json').read_text())
    folder=next(x for x in before['folders'] if x['name']=='Product Strategy')
    candidates=[x for x in before['files'] if x.get('parentFolderId')==folder['id'] and not x['trashed']]
    target=max(candidates,key=lambda x:x['updatedAt']); expected=json.loads(json.dumps(before))
    next(x for x in expected['files'] if x['id']==target['id'])['starred']=True
    return {'onlyExpectedStarChanged':after==expected,'fileNamed':target['name'].lower() in final.lower(),
            'modifiedDateReported':target['updatedAt'][:10] in final}

from pathlib import Path
import argparse, os
parser=argparse.ArgumentParser(description='Export pinned iOSWorld source seed data without launching an app')
parser.add_argument('--ios-source',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
parser.add_argument('--timezone',default='America/Los_Angeles')
parser.add_argument('apps',nargs='*')
args=parser.parse_args()
root=args.ios_source.resolve()/'iphone/apps'
out=args.output.resolve();out.mkdir(parents=True,exist_ok=True)
os.environ['TZ']=args.timezone

import subprocess,json,hashlib,concurrent.futures,sys,re
plans={
 'freshcart':[('catalog','try SeededCatalogRepository().loadCatalog()'),('state','SeededCatalogRepository.initialState()')],
 'teamchat':[('workspaces','SeedDataFactory.makeSeededWorkspaces()')],
 'ticketbox': [('venues','SeedData.makeVenues()'),('performers','SeedData.makePerformers()'),('events','SeedData.makeEvents(venues: SeedData.makeVenues(), performers: SeedData.makePerformers())'),('pastTickets','SeedData.makePastTickets()')],
 'cityride':[('state','SeedData.initialState()'),('places','SeedData.places'),('estimates','SeedData.allRouteEstimates()')],
 'megamart':[('catalog','SeedData.seededCatalog()'),('state','SeedData.initialState(snapshotMetadata: nil)')],
 'dinespot':[('cities','SeedData.cities()'),('restaurants','SeedData.restaurants()'),('availability','SeedData.seededAvailability(for: SeedData.restaurants())'),('reservations','SeedData.seededReservations()'),('saved','SeedData.seededSavedRestaurants()')],
 'skytrip':[('state','SeedDataFactory.seededState()'),('itineraries','SeedDataFactory.seededItineraries()'),('trips','SeedDataFactory.seededTrips()'),('airports','SeedDataFactory.airports')],
 'trailblaze':[('state','SeedData.makeSeededData()')],
 'caltrack':[('state','SeedData.seededState()'),('exercises','SeedData.seededExercises()')],
 'tasterank':[('restaurants','SeedData.restaurants()'),('visits','SeedData.defaultVisitLogs(restaurants: SeedData.restaurants())'),('lists','SeedData.defaultLists(restaurants: SeedData.restaurants())'),('friends','SeedData.friends()'),('profile','SeedData.userProfile()')],
 'lockedin':[('profile','SeedDataFactory.currentUserProfile'),('connections','SeedDataFactory.connections'),('posts','SeedDataFactory.makePosts()'),('jobs','SeedDataFactory.jobs'),('conversations','SeedDataFactory.makeConversations()'),('notifications','SeedDataFactory.allNotifications')],
 'scorezone':[('teams','SeedData.teams'),('headlines','SeedData.headlines'),('games','SeedData.allSeededGames'),('favoriteTeams','SeedData.defaultFavoriteTeamIDs'),('profile','SeedData.profile')],
}
def run(app):
 files=[p for p in (root/app).rglob('*.swift') if p.parent.name in ['Models','Utilities','Resources','Persistence'] and p.name not in ['AppStore.swift','LLMService.swift'] and p.read_text().startswith('import Foundation')]
 text='\n'.join(p.read_text() for p in files)
 text=re.sub(r'(?<![A-Za-z0-9_])Date\(\)', 'Date(timeIntervalSince1970:1772712000)', text)
 if app=='caltrack':
  resource=next((root/app).rglob('food_database.json'))
  text=text.replace('bundle.url(forResource: "food_database", withExtension: "json")', 'Optional(URL(fileURLWithPath: '+json.dumps(str(resource))+'))')
 text+='\nlet encoder = JSONEncoder(); encoder.dateEncodingStrategy = .iso8601\nvar result: [String:Any] = [:]\nfunc add<T:Encodable>(_ key:String, _ value:T) throws { result[key] = try JSONSerialization.jsonObject(with: encoder.encode(value), options: [.fragmentsAllowed]) }\n'
 for key,expr in plans[app]:text+=f'try add("{key}", {expr})\n'
 text+='print(String(data: try JSONSerialization.data(withJSONObject: result, options: [.prettyPrinted,.sortedKeys]),encoding:.utf8)!)\n'
 swift=out/(app+'.swift');swift.write_text(text)
 proc=subprocess.run(['swift',str(swift)],capture_output=True,text=True,timeout=90)
 (out/(app+'.stderr')).write_text(proc.stderr)
 if proc.returncode==0:
  data=json.loads(proc.stdout);(out/(app+'.json')).write_text(json.dumps(data,ensure_ascii=False,indent=2));(out/(app+'-provenance.json')).write_text(json.dumps({'sources':{str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in files},'timezone':args.timezone,'transformation':'Date() frozen to 2026-03-05T12:00:00Z; Foundation model/seed compilation only; no app launched'},indent=2))
 return app,proc.returncode,proc.stderr[:600]
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
 for result in pool.map(run,args.apps or plans):print(result,flush=True)

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

import json,subprocess,hashlib
for app in ['mail','cinephile']:
 if app=='mail':
  p=root/'mail/xproj/Mail/ViewController.swift';s=p.read_text();models=s[s.index('enum MailFolder:'):s.index('private enum MailTheme')];methods=s[s.index('    private static func makeItem('):s.index('\nstruct MailNotificationPayload')]
  # methods include the original MailStore closing brace; omit persistence and account imports.
  text='import Foundation\n'+models+s[s.index('private func formatMailAttachmentCount'):s.index('private func mailInitials')]+'\nenum MailTheme { static let accountEmail="jordan.avery@email.com" }\nenum MailStore {\n'+methods.replace('private static func','static func')
  text+='\nlet items=MailStore.seedItems(now:Date(timeIntervalSince1970:1772712000));let output:[String:Any] = ["items":items.map { ["id":$0.id.uuidString,"threadId":$0.threadId,"from":$0.from,"to":$0.to,"subject":$0.subject,"body":$0.body,"date":ISO8601DateFormatter().string(from:$0.date),"folder":$0.folder.title,"category":$0.category.title,"isUnread":$0.isUnread] as [String:Any] }]\n'
 else:
  p=root/'cinephile/xproj/Cinephile/Shared/flux/state/MoviesState.swift';s=p.read_text();seed=s[s.index('let discoverSeedMovies:'):s.index('\n]\n')+3]
  text='''import Foundation
struct Genre:Codable { let id:Int;let name:String }
struct Movie:Codable { let id:Int;let original_title:String;let title:String;let overview:String;let poster_path:String;let backdrop_path:String;let popularity:Double;let vote_average:Double;let vote_count:Int;let release_date:String;let genres:[Genre];let runtime:Int;let status:String;let video:Bool }
'''+seed
  import re
  wishlist=re.search(r'var wishlist: Set<Int> = (\[[^\]]+\])',s)[1];seen=re.search(r'var seenlist: Set<Int> = (\[[^\]]+\])',s)[1]
  text+='\nlet movies=try JSONSerialization.jsonObject(with:JSONEncoder().encode(discoverSeedMovies));let output:[String:Any] = ["movies":movies,"wishlist":'+wishlist+',"seenlist":'+seen+']\n'
 text+='print(String(data:try JSONSerialization.data(withJSONObject:output,options:[.prettyPrinted,.sortedKeys]),encoding:.utf8)!)\n'
 target=out/(app+'.swift');target.write_text(text);proc=subprocess.run(['swift',str(target)],capture_output=True,text=True,timeout=90);print(app,proc.returncode,proc.stderr[:1500]);(out/(app+'.stderr')).write_text(proc.stderr)
 if proc.returncode==0:
  data=json.loads(proc.stdout);(out/(app+'.json')).write_text(json.dumps(data,ensure_ascii=False,indent=2));(out/(app+'-provenance.json')).write_text(json.dumps({'source':str(p.relative_to(root)),'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'timezone':args.timezone,'transformation':'Original seed declarations compiled without app UI/network/persistence; reference date 2026-03-05T12:00Z'},indent=2))

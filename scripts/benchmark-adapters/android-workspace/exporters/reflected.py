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

import subprocess,json,hashlib,sys,re
mirror='''
func plain(_ value: Any, depth:Int = 0) -> Any {
 if depth > 20 { return "depth-limit" }
 if let v=value as? String { return v }; if let v=value as? Date { return ISO8601DateFormatter().string(from:v) }; if let v=value as? UUID { return v.uuidString }
 if let v=value as? Bool { return v }; if let v=value as? Int { return v }; if let v=value as? Double { return v }
 let m=Mirror(reflecting:value)
 if m.displayStyle == .optional { return m.children.first.map { plain($0.value,depth:depth+1) } ?? NSNull() }
 if m.displayStyle == .collection || m.displayStyle == .set { return m.children.map { plain($0.value,depth:depth+1) } }
 if m.children.isEmpty { return String(describing:value) }
 return Dictionary(uniqueKeysWithValues:m.children.enumerated().map { ($0.element.label ?? String($0.offset),plain($0.element.value,depth:depth+1)) })
}
'''
for app in args.apps:
 if app=='quickchat':
  p=root/app/'xproj/QuickChat/ViewController.swift'; source=p.read_text(); lines=source.splitlines()
  text='import Foundation\nimport SwiftUI\n'+'\n'.join(lines[132:3540])+'\nprivate func adding(_ component:Calendar.Component,_ value:Int,to date:Date)->Date { Calendar.current.date(byAdding:component,value:value,to:date)! }\n}\n'+'\n'.join(lines[4562:4571])
  text=text.replace('OpenAIReplyGenerator.makeIfConfigured()','nil')
  text+='\nenum AppTheme { static let error=Color.red; static let accent=Color.blue; static let secondaryText=Color.gray }\nextension Array { subscript(safe index:Int)->Element? { indices.contains(index) ? self[index] : nil } }\n'
  text+=mirror+'\nlet s=MessagesStore(); let output:[String:Any] = ["currentUserID":s.currentUserID.uuidString,"contacts":plain(s.contacts),"chats":plain(s.chats),"profile":plain(s.profile),"channels":plain(s.channels),"calls":plain(s.calls)]\n'
 elif app=='notes':
  p=root/app/'xproj/Notes/NotesApp.swift'; models=(p.parent/'Item.swift').read_text().replace('import SwiftData','').replace('@Model','');models='\n'.join(l for l in models.splitlines() if '@Relationship' not in l)
  text=models+'\n'+p.read_text().split('private struct SeedGraph {')[1];text=models+'\nprivate struct SeedGraph {'+p.read_text().split('private struct SeedGraph {')[1]
  text+='\nprivate let g=SeedGraph.build(); let output:[String:Any] = ["folders":g.folders.map { ["name":$0.name,"sortOrder":$0.sortOrder] },"notes":g.notes.map { ["title":$0.title,"body":$0.body,"folder":$0.folder?.name ?? "","isPinned":$0.isPinned,"isInTrash":$0.isInTrash] as [String:Any] }]\n'
 elif app=='stayfinder':
  p=root/app/'xproj/StayFinder/ViewController.swift'; lines=p.read_text().splitlines()
  text='import Foundation\nimport AppKit\ntypealias UIColor = NSColor\n'+'\n'.join(lines[25:393])+'\n'+'\n'.join(lines[510:527])+'\n'+'\n'.join(lines[544:2612])+'\n}\n'
  text=text.replace('        loadPersistedFavoritesAndWishlists()','')
  text+=mirror+'\nlet s=SfStore.shared; let output:[String:Any] = ["listings":plain(s.listings),"bookings":plain(s.bookings),"conversations":plain(s.conversations),"profile":plain(s.profile),"wishlists":plain(s.wishlists)]\n'
 text=re.sub(r'(?<![A-Za-z0-9_])Date\(\)', 'Date(timeIntervalSince1970:1772712000)', text)
 text+='print(String(data:try JSONSerialization.data(withJSONObject:output,options:[.prettyPrinted,.sortedKeys]),encoding:.utf8)!)\n'
 if app=='quickchat':
  s=text
  token=re.compile(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|//[^\n]*|/\*[\s\S]*?\*/|[\[\](){},]')
  for m in list(re.finditer(r'^        (contacts|chats|statuses|channels|communities|calls|scheduledCalls|messageLists|broadcastLists|linkedDevices) = \[',s,re.M))[::-1]:
   start=m.end(); depth=0; chunks=[];last=start;end=None
   for t in token.finditer(s,start):
    c=t.group()
    if len(c)>1: continue
    if c in '[({': depth+=1
    elif c in '])}':
     if depth==0 and c==']':
      if s[last:t.start()].strip():chunks.append(s[last:t.start()])
      end=t.end();break
     depth-=1
    elif c==',' and depth==0:chunks.append(s[last:t.start()]);last=t.end()
   if chunks and end:
    name=m.group(1);new=f'        {name} = []\n'+'\n'.join(f'        {name}.append({x.strip()})' for x in chunks if x.strip());s=s[:m.start()]+new+s[end:]
  text=s
 target=out/(app+'.swift');target.write_text(text)
 proc=subprocess.run(['swift',str(target)],capture_output=True,text=True,timeout=120);(out/(app+'.stderr')).write_text(proc.stderr)
 print(app,proc.returncode,proc.stderr[:2000],flush=True)
 if proc.returncode==0:
  data=json.loads(proc.stdout);(out/(app+'.json')).write_text(json.dumps(data,ensure_ascii=False,indent=2))
  (out/(app+'-provenance.json')).write_text(json.dumps({'source':str(p.relative_to(root)),'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'timezone':args.timezone,'transformations':['Seed/model extraction without app UI','Date() fixed to 2026-03-05T12:00:00Z','Persistence and network initialization not executed','Business data reflected to JSON; presentation-only color objects are not evaluated as task evidence']},indent=2))

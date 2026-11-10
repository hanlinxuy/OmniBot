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

import json,subprocess,re,hashlib
p=root/'quickbite/xproj/QuickBite/ViewController.swift';lines=p.read_text().splitlines()
text='''import Foundation
import AppKit
import CoreLocation
typealias UIColor = NSColor
struct UIImage { init?(named:String) { return nil }; init?(systemName:String) { return nil } }
'''+ p.read_text()[p.read_text().index('struct DashCategory'):p.read_text().index('private func configureHeroImageView')]+'''
final class DashStore {
let categories:[DashCategory];let browseCategories:[DashCategory];var restaurants:[Restaurant];var addresses:[Address];var selectedAddressID:UUID;var pastOrders:[ActiveOrder]=[]
'''+ '\n'.join(lines[701:3711])+'\n}\n'
text=text.replace('private init()', 'init()').replace('        paymentAccounts = accountsService.loadAccounts()','').replace('        selectedPaymentAccountId = preferredCheckoutAccount()?.id','').replace('        loadPersistedCart()','')
text=re.sub(r'(?<![A-Za-z0-9_])Date\(\)', 'Date(timeIntervalSince1970:1772712000)',text)
mirror=(out/'quickchat.swift').read_text().split('func plain(')[1].split('\nlet s=MessagesStore()')[0]
text+='\nfunc plain('+mirror+'\nlet s=DashStore();let output:[String:Any] = ["restaurants":plain(s.restaurants),"addresses":plain(s.addresses),"pastOrders":plain(s.pastOrders)]\nprint(String(data:try JSONSerialization.data(withJSONObject:output,options:[.prettyPrinted,.sortedKeys]),encoding:.utf8)!)\n'
target=out/'quickbite.swift';target.write_text(text);proc=subprocess.run(['swift',str(target)],capture_output=True,text=True,timeout=120);(out/'quickbite.stderr').write_text(proc.stderr);print(proc.returncode,proc.stderr[:1600])
if proc.returncode==0:
 data=json.loads(proc.stdout);(out/'quickbite.json').write_text(json.dumps(data,ensure_ascii=False,indent=2));(out/'quickbite-provenance.json').write_text(json.dumps({'source':str(p.relative_to(root)),'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'timezone':args.timezone,'transformation':'Original restaurant/address/order seed initializer; payment account service and persisted cart loading removed; UI images omitted; date fixed to 2026-03-05'},indent=2))

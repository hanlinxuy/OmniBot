import pathlib,json,argparse,re
p=argparse.ArgumentParser();p.add_argument('--artifacts',type=pathlib.Path,required=True);p.add_argument('--output',type=pathlib.Path,required=True);a=p.parse_args()
previous=json.loads(a.output.read_text()) if a.output.exists() else {'runs':[]}
runs={x['artifact']:x for x in previous['runs']}
for d in sorted(a.artifacts.glob('ios-android-*')):
 if not (d/'result.json').exists():
  if not (d/'failure.json').exists():continue
  failure=json.loads((d/'failure.json').read_text()); match=re.fullmatch(r'ios-android-(.+)-(qwen|deepseek)-[a-f0-9]+',d.name)
  if not match:continue
  cleanup=json.loads((d/'cleanup.json').read_text()) if (d/'cleanup.json').exists() else {}
  runs[d.name]={'artifact':d.name,'sourceTask':match[1],'provider':'35a3' if match[2]=='qwen' else match[2],'officialBenchmarkScore':None,'acceptance':{'passed':False},'cleanup':cleanup,'failureType':failure.get('type'),'classification':failure.get('classification','runtime_failure'),'providerRequests':failure.get('providerRequests',0),'completedRun':False}
  continue
 raw=json.loads((d/'result.json').read_text());cleanup=json.loads((d/'cleanup.json').read_text()) if (d/'cleanup.json').exists() else {};review=json.loads((d/'independent-review.json').read_text()) if (d/'independent-review.json').exists() else None
 acceptance=dict(raw['acceptance'])
 if review is not None: acceptance['passed']=bool(review['passed'])
 acceptance['passed']=bool(acceptance.get('passed') and cleanup.get('workspaceAbsent'))
 row={'completedRun':True,'artifact':d.name,'sourceTask':raw['sourceTask'],'provider':'35a3' if raw['provider']=='qwen' else raw['provider'],'model':raw.get('model'),'officialBenchmarkScore':None,'transport':raw.get('transport','shell'),'cleanup':cleanup,'rawAcceptance':raw['acceptance'],'acceptance':acceptance,'elapsedSeconds':raw['elapsedSeconds'],'providerRequests':raw['providerRequests']}
 if review:row['independentReview']={k:v for k,v in review.items() if k!='evidenceSha256'}
 runs[d.name]=row
result={'scope':'Android phone Pi source-backed file/API adaptations, not original GUI scores; historical failures retained','runs':list(runs.values())}
a.output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'runs':len(runs),'attemptedTasks':len({r['sourceTask'] for r in runs.values()}),'tasksWithAnyPass':len({r['sourceTask'] for r in runs.values() if r.get('acceptance',{}).get('passed')})}))

import importlib.util,sys,tempfile,types,pathlib,json,unittest.mock
p=pathlib.Path(__file__).with_name('live-smoke.py'); spec=importlib.util.spec_from_file_location('smoke',p); m=importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
class Backend:
 def __init__(self,*a): self.req=''; self.count=0; self.stopped=False; self.transition=True; self.partial=True; self.sends=0
 def unlocked(self): pass
 def ensure_primary_display(self): pass
 def check_access(self):
  if self.stopped and self.transition: self.transition=False; raise RuntimeError('app_access_state_unknown')
 def start(self): return self
 def stop_uiautomator(self): self.stopped=True
 def shell(self,*a):
  if a[0]=='am': self.req=a[a.index('requestId')+1]; self.count+=1; return ''
  if a[-1]=='files/run_logs': return '' if not self.stopped else '1234567890abcdef_run.json'
  if 'files/run_logs/' in a[-1]: return json.dumps({'run_id':'run','goal':'计算 12 34','status':'success','success':True,'steps':[]})
  if self.partial: self.partial=False; return '{'
  return json.dumps({'request_id':self.req,'success':True,'state':{'package_name':m.CALCULATOR,'xml':'<hierarchy><node package="com.android.bbkcalculator" text="'+('0' if self.count==1 else '408')+'" /></hierarchy>'}})
class Lock:
 def __enter__(self): pass
 def __exit__(self,*a): pass
class Runtime:
 def __init__(self,backend,root,run): self.backend=backend; self.pause_file=pathlib.Path('/nonexistent-pause')
 def lock(self): return Lock()
 def guard(self): pass
 def call(self,op,a,recover=False):
  if op=='observe': return {'ok':True,'observation_id':'a','context':{'package':m.OMNIBOT},'nodes':[{'visible':True,'description':'发送','text':'','class_name':'android.widget.Button'},{'visible':True,'description':'','text':'','class_name':'android.widget.EditText'}]}
  if op=='tap' and a.get('selector',{}).get('description')=='发送': self.backend.sends+=1; assert self.backend.sends==1
  if op=='type_text': return {'ok':True,'value_verified':True}
  return {'ok':True}
sys.modules['vivo_phone_use']=types.ModuleType('vivo_phone_use'); b=types.ModuleType('vivo_phone_use.backend'); b.AndroidBackend=Backend; sys.modules[b.__name__]=b; r=types.ModuleType('vivo_phone_use.runtime'); r.Runtime=Runtime; sys.modules[r.__name__]=r
with tempfile.TemporaryDirectory() as d, unittest.mock.patch.object(m.shutil,'which',return_value='/adb'), unittest.mock.patch.object(m.time,'sleep'), unittest.mock.patch.object(sys,'argv',['live-smoke','--live','--serial','x','--device-runtime-root',d,'--output',d+'/attempt.json','--composer-already-new']):
 assert m.main()==0
 x=json.load(open(d+'/attempt.json')); assert x['status']=='independent_success' and x['run_id']=='run' and x['evidence'] and x['marker'] in x['prompt']
print('PASS mock: one submission, native observations, fresh transition, evidence')

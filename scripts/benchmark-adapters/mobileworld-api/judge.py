"""Execute pinned MobileWorld SQL helpers and judge bodies against an owned backend.

Only backend transport and initialized-state plumbing are replaced. The source task's
is_successful body and its SQL helper definitions are compiled unchanged. This is
source-judge acceptance for an API adaptation, not the official GUI benchmark.
"""
import ast
import datetime
import json
import logging
import pathlib
import re
import shlex
import subprocess
import time
import types


class JsonRow(dict):
    def __init__(self, pairs):
        super().__init__(pairs)
        self.columns=pairs


def judge_error(message):
    raise RuntimeError('Source SQL helper failed: '+str(message))


class Database:
    def __init__(self, ssh, compose, queries):
        self.ssh=ssh;self.compose=compose;self.rows=[];self.queries=queries

    def execute(self, query, params=()):
        if not query.lstrip().upper().startswith('SELECT'): raise ValueError('Read-only source judge queries only')
        proof={'completed':False};self.queries.append(proof)
        for value in params:
            literal=str(value) if isinstance(value,(int,float)) else "'"+str(value).replace("'","''")+"'"
            query=query.replace('%s',literal,1)
        if '%s' in query: raise ValueError('Missing source SQL parameters')
        query="SELECT COALESCE(json_agg(row_to_json(q)), '[]'::json) FROM ("+query.strip().rstrip(';')+") q;"
        command='docker compose -f '+shlex.quote(self.compose)+' exec -T db psql -X -U postgres -d mastodon -At -v ON_ERROR_STOP=1'
        output=subprocess.check_output(self.ssh+[command],input=query,text=True,timeout=30)
        self.rows=[tuple(datetime.datetime.fromisoformat(re.sub(r'\.(\d{1,6})(?=$|[+-])',lambda m:'.'+m[1].ljust(6,'0'),v)) if k.endswith('_at') and isinstance(v,str) else v for k,v in row.columns) for row in json.loads(output,object_pairs_hook=JsonRow)]

        proof['completed']=True

    def fetchall(self): return self.rows
    def fetchone(self): return self.rows[0] if self.rows else None
    def close(self): pass


def judge(source, task_file, ssh, compose):
    source=pathlib.Path(source)
    helper_file=source/'src/mobile_world/runtime/app_helpers/mastodon.py'
    helper_ast=ast.parse(helper_file.read_text())
    helper_functions=[node for node in helper_ast.body if isinstance(node,(ast.FunctionDef,ast.AsyncFunctionDef)) or isinstance(node,ast.Assign) and isinstance(node.value,ast.Constant)]
    namespace={'datetime':datetime.datetime,'json':json,'logger':types.SimpleNamespace(error=judge_error,info=lambda *_:None)}
    module=ast.Module(body=[ast.ImportFrom(module='__future__',names=[ast.alias(name='annotations')],level=0),*helper_functions],type_ignores=[])
    exec(compile(ast.fix_missing_locations(module),str(helper_file),'exec'),namespace)
    queries=[]
    namespace['connect_to_postgres']=lambda:(Database(ssh,compose,queries),Database(ssh,compose,queries))
    namespace['is_mastodon_healthy']=lambda:True  # Reachability is checked by SQL transport, independently of model claims.
    task_path=source/'src/mobile_world/tasks/definitions/mastodon'/task_file
    task_ast=ast.parse(task_path.read_text());definition=next(n for n in task_ast.body if isinstance(n,ast.ClassDef))
    # Source initialize/teardown call hard-coded /app services. The runner instead
    # restores and cleans its dedicated compose project before/after every task.
    definition.bases=[]
    scope={'mastodon':types.SimpleNamespace(**namespace),'time':time,'logger':logging.getLogger('source-task')}
    task_module=ast.Module(body=[ast.ImportFrom(module='__future__',names=[ast.alias(name='annotations')],level=0),definition],type_ignores=[])
    exec(compile(ast.fix_missing_locations(task_module),str(task_path),'exec'),scope)
    task=scope[definition.name]();task._check_is_initialized=lambda:None
    result=task.is_successful(None)
    if not queries or any(not x['completed'] for x in queries):raise RuntimeError('Source judge SQL transport/parsing failed; no model score')
    return result

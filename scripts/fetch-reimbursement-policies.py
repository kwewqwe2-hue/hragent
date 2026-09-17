"""Repeatable official-source refresh; writes review snapshots, never auto-publishes changed law."""
from pathlib import Path
import importlib.util,json,concurrent.futures
root=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('official',root/'scripts/fetch-national-policy-library.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
m.OUT=root/'.artifacts/reimbursement-review/sources';m.OUT.mkdir(parents=True,exist_ok=True)
rows=json.loads((root/'n8nwork/knowledge-files/reimbursement-policy-catalog.json').read_text(encoding='utf-8'))
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
 results=list(pool.map(m.fetch,rows))
for s in results:print(s['id'],s.get('error','OK'),len(s.get('content','')))
(m.OUT/'report.json').write_text(json.dumps([{k:v for k,v in s.items() if k not in ('content','links','images')} for s in results],ensure_ascii=False,indent=2),encoding='utf-8')

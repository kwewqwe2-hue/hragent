from pathlib import Path
import importlib.util,json,concurrent.futures
root=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('official',root/'scripts/fetch-national-policy-library.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
m.OUT=root/'.artifacts/hrmanual-cleanup/sources';m.OUT.mkdir(exist_ok=True)
rows=json.loads((root/'n8nwork/knowledge-files/hrmanual-replacements.json').read_text(encoding='utf-8'))
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
 for s in pool.map(m.fetch,rows):print(s['id'],s.get('error','OK'),len(s.get('content','')))

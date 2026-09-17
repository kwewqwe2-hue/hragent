"""Create a policy-only artifact over the running stable application; no workspace source is reverted."""
from pathlib import Path
import hashlib, json, zipfile
root=Path(__file__).resolve().parents[1]/'.artifacts/policy-review/release'
allowed=('com/hragent/hragentv1/service/PolicyMonitorService', 'com/hragent/hragentv1/service/PolicyCopilotService', 'com/hragent/hragentv1/dto/DemoPolicyDtos')
def selected(name): return any(name==x+'.class' or (name.startswith(x+'$') and name.endswith('.class')) for x in allowed)
compiled=root/'compiled'
replacement={p.relative_to(compiled).as_posix():p.read_bytes() for p in compiled.rglob('*.class') if selected(p.relative_to(compiled).as_posix())}
assert len(replacement)>=8, replacement.keys()
classes=root/'isolated-classes'; classes.mkdir(exist_ok=True)
with zipfile.ZipFile(root/'baseline.jar') as before, zipfile.ZipFile(root/'policy-only.jar','w') as after:
 for entry in before.infolist():
  data=before.read(entry.filename)
  if entry.filename.startswith('BOOT-INF/classes/'):
   rel=entry.filename[len('BOOT-INF/classes/'):]
   if rel in replacement: data=replacement.pop(rel)
   if rel and not entry.is_dir():
    target=classes/rel; target.parent.mkdir(parents=True,exist_ok=True); target.write_bytes(data)
  after.writestr(entry,data)
 assert not replacement, replacement.keys()
with zipfile.ZipFile(root/'baseline.jar') as a, zipfile.ZipFile(root/'policy-only.jar') as b:
 assert set(a.namelist())==set(b.namelist())
 changed=[n for n in a.namelist() if a.read(n)!=b.read(n)]
 assert changed and all(n.startswith('BOOT-INF/classes/') and selected(n[len('BOOT-INF/classes/'):]) for n in changed)
report={'baselineSha256':hashlib.sha256((root/'baseline.jar').read_bytes()).hexdigest(),'outputSha256':hashlib.sha256((root/'policy-only.jar').read_bytes()).hexdigest(),'changedEntries':changed}
(root/'overlay-manifest.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(report,ensure_ascii=False,indent=2))

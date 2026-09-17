"""Package only national retrieval changes on the running baseline, preserving other work."""
from pathlib import Path
import hashlib, json, shutil, zipfile
base=Path(__file__).resolve().parents[1]
out=base/'.artifacts/national-policy-library/release'
compiled=base/'hragentv1/backend/target/classes'
names=['com/hragent/hragentv1/service/PolicyCopilotService.class','com/hragent/hragentv1/service/NationalPolicySearch.class']
replacement={f'BOOT-INF/classes/{n}':(compiled/n).read_bytes() for n in names}
with zipfile.ZipFile(out/'baseline.jar') as original, zipfile.ZipFile(out/'app.jar','w') as result:
    for item in original.infolist():
        result.writestr(item,replacement.pop(item.filename,original.read(item.filename)))
    for name,data in replacement.items(): result.writestr(name,data,compress_type=zipfile.ZIP_DEFLATED)
with zipfile.ZipFile(out/'baseline.jar') as a, zipfile.ZipFile(out/'app.jar') as b:
    assert set(a.namelist()).issubset(b.namelist())
    changed=[n for n in b.namelist() if n not in a.namelist() or a.read(n)!=b.read(n)]
    assert set(changed)=={f'BOOT-INF/classes/{n}' for n in names},changed
    isolated=out/'isolated-classes'
    for n in b.namelist():
        if n.startswith('BOOT-INF/classes/') and not n.endswith('/'):
            p=isolated/n[len('BOOT-INF/classes/'):];p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b.read(n))
(out/'overlay-manifest.json').write_text(json.dumps({'changedEntries':changed,'sha256':hashlib.sha256((out/'app.jar').read_bytes()).hexdigest()},indent=2),encoding='utf-8')
(out/'Dockerfile').write_text('FROM hragent-national-backend-baseline-20260911\nCOPY app.jar /app/app.jar\n',encoding='utf-8')
front=out/'frontend';front.mkdir(exist_ok=True)
shutil.copytree(base/'hragentv1/frontend/dist',front/'dist',dirs_exist_ok=True)
(front/'Dockerfile').write_text('FROM hragent-national-frontend-baseline-20260911\nCOPY dist/ /usr/share/nginx/html/\n',encoding='utf-8')
print('Only the two policy retrieval classes changed; frontend bundle prepared.')

from pathlib import Path
import zipfile,json,hashlib
root=Path(__file__).resolve().parents[1];out=root/'.artifacts/reimbursement-review/release'
name='BOOT-INF/classes/com/hragent/hragentv1/service/OfficialPolicyCrawler.class';compiled=root/'hragentv1/backend/target/classes'/name[len('BOOT-INF/classes/'):]
assert compiled.exists()
with zipfile.ZipFile(out/'baseline.jar') as a,zipfile.ZipFile(out/'app.jar','w') as b:
 for entry in a.infolist():b.writestr(entry,compiled.read_bytes() if entry.filename==name else a.read(entry.filename))
with zipfile.ZipFile(out/'baseline.jar') as a,zipfile.ZipFile(out/'app.jar') as b:
 assert a.namelist()==b.namelist();changed=[n for n in a.namelist() if a.read(n)!=b.read(n)];assert changed==[name]
(out/'manifest.json').write_text(json.dumps({'changed':changed,'sha256':hashlib.sha256((out/'app.jar').read_bytes()).hexdigest()},indent=2),encoding='utf8')
(out/'Dockerfile').write_text('FROM hragent-reimbursement-baseline\nCOPY app.jar /app/app.jar\n',encoding='utf8')
print('Only OfficialPolicyCrawler.class changed; other business code preserved.')

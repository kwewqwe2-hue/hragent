from pathlib import Path
import zipfile,json,shutil,hashlib
root=Path(__file__).resolve().parents[1];out=root/'.artifacts/hrmanual-cleanup/release'
name='BOOT-INF/classes/com/hragent/hragentv1/service/AdminService.class';compiled=root/'hragentv1/backend/target/classes'/name[len('BOOT-INF/classes/'):]
with zipfile.ZipFile(out/'baseline.jar') as a,zipfile.ZipFile(out/'app.jar','w') as b:
 for item in a.infolist():b.writestr(item,compiled.read_bytes() if item.filename==name else a.read(item.filename))
with zipfile.ZipFile(out/'baseline.jar') as a,zipfile.ZipFile(out/'app.jar') as b:
 assert a.namelist()==b.namelist();changed=[n for n in a.namelist() if a.read(n)!=b.read(n)];assert changed==[name],changed
(out/'manifest.json').write_text(json.dumps({'changedEntries':changed,'sha256':hashlib.sha256((out/'app.jar').read_bytes()).hexdigest()},indent=2),encoding='utf8')
(out/'Dockerfile').write_text('FROM hragent-hrmanual-backend-baseline\nCOPY app.jar /app/app.jar\n',encoding='utf8')
for target,folder in [('frontend','hragentv1/frontend'),('chat','hragent-chat')]:
 p=out/target;p.mkdir(exist_ok=True);shutil.copytree(root/folder/'dist',p/'dist',dirs_exist_ok=True)
 (p/'Dockerfile').write_text('FROM hragent-hrmanual-'+target+'-baseline\nCOPY dist/ /usr/share/nginx/html/\n',encoding='utf8')
print('Prepared AdminService-only backend, two-domain frontend, and requested chat wording.')

from pathlib import Path
import zipfile,json,hashlib
root=Path(__file__).resolve().parents[1];out=root/'.artifacts/template-attachment-fix/release'
names=['BOOT-INF/classes/com/hragent/hragentv1/service/LifecycleService.class','BOOT-INF/classes/com/hragent/hragentv1/web/WebChatController.class']
with zipfile.ZipFile(out/'baseline.jar') as a,zipfile.ZipFile(out/'app.jar','w') as b:
 for entry in a.infolist():
  file=root/'hragentv1/backend/target/classes'/entry.filename.removeprefix('BOOT-INF/classes/')
  b.writestr(entry,file.read_bytes() if entry.filename in names else a.read(entry.filename))
with zipfile.ZipFile(out/'baseline.jar') as a,zipfile.ZipFile(out/'app.jar') as b:
 assert a.namelist()==b.namelist()
 changed=[n for n in a.namelist() if a.read(n)!=b.read(n)]
 assert set(changed)==set(names),changed
(out/'manifest.json').write_text(json.dumps({'changed':changed,'sha256':hashlib.sha256((out/'app.jar').read_bytes()).hexdigest()},indent=2))
(out/'Dockerfile').write_text('FROM hragent-template-fix-baseline\nCOPY app.jar /app/app.jar\n')
print('Only LifecycleService and WebChatController patched.')

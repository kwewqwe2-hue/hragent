from pathlib import Path
import zipfile,tarfile,json,hashlib
root=Path(__file__).resolve().parents[1];out=root/'.artifacts/template-autofill';release=out/'release'
with tarfile.open(out/'test-output.tgz') as t:t.extractall(out/'compiled',filter='data')
base=out/'compiled/target/classes/com/hragent/hragentv1/service'
families=['LifecycleService','EmploymentCertificateService','VisaCertificateDocumentService','CertificateTemplatePreparationService']
files={('BOOT-INF/classes/com/hragent/hragentv1/service/'+f.name):f.read_bytes() for f in base.glob('*.class') if any(f.stem==n or f.stem.startswith(n+'$') for n in families)}
assert all(any(n+'.class' in key for key in files) for n in families)
with zipfile.ZipFile(release/'baseline.jar') as a,zipfile.ZipFile(release/'app.jar','w') as b:
 for entry in a.infolist():b.writestr(entry,files.get(entry.filename,a.read(entry.filename)))
 for name,data in files.items():
  if name not in a.namelist():b.writestr(name,data)
with zipfile.ZipFile(release/'baseline.jar') as a,zipfile.ZipFile(release/'app.jar') as b:
 changed=[n for n in b.namelist() if n not in a.namelist() or a.read(n)!=b.read(n)]
 assert set(changed)<=set(files)
(release/'manifest.json').write_text(json.dumps({'changed':changed,'sha256':hashlib.sha256((release/'app.jar').read_bytes()).hexdigest()},indent=2))
(release/'Dockerfile').write_text('FROM hragent-template-autofill-baseline\nCOPY app.jar /app/app.jar\n')
print('Packaged template preparation and certificate service changes:',len(changed),'classes; all other code preserved.')

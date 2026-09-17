from pathlib import Path
import zipfile, tarfile, json

root = Path(__file__).resolve().parents[1]
out = root / '.artifacts/intent-flow'
with tarfile.open(out / 'test-output.tgz') as archive:
    archive.extractall(out / 'compiled', filter='data')
classes = out / 'compiled/target/classes/com/hragent/hragentv1'
families = {'service': ['EmployeeIntentUnderstanding', 'ServiceConversationGuide', 'LifecycleService', 'WebChatGatewayService', 'AnnualLeaveConversation'], 'web': ['WebChatController']}
replacements = {}
for folder, names in families.items():
    for file in (classes / folder).glob('*.class'):
        if any(file.stem == name or file.stem.startswith(name + '$') for name in names):
            replacements['BOOT-INF/classes/com/hragent/hragentv1/' + folder + '/' + file.name] = file.read_bytes()
    assert all(any(key.endswith('/' + name + '.class') for key in replacements) for name in names)
with zipfile.ZipFile(out / 'baseline.jar') as old, zipfile.ZipFile(out / 'app.jar', 'w') as new:
    original = set(old.namelist())
    for entry in old.infolist():
        new.writestr(entry, replacements.get(entry.filename, old.read(entry.filename)))
    for key, data in replacements.items():
        if key not in original:
            new.writestr(key, data)
with zipfile.ZipFile(out / 'baseline.jar') as old, zipfile.ZipFile(out / 'app.jar') as new:
    changed = [key for key in new.namelist() if key not in old.namelist() or old.read(key) != new.read(key)]
    assert set(changed) <= set(replacements)
(out / 'manifest.json').write_text(json.dumps(changed, indent=2))
(out / 'Dockerfile').write_text('FROM hragent-intent-flow-baseline\nCOPY app.jar /app/app.jar\n')
print('Packaged conversation changes:', len(changed), 'classes; other running code preserved.')

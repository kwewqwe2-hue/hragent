from pathlib import Path
import ast, shutil
root = Path(__file__).resolve().parents[1]
for relative in ['hragent-chat/src/App.vue', 'hragentv1/frontend/src/views/EmployeeServicesView.vue']:
    p = root / relative
    target = root / '.backups/before-concise-chat' / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    if not target.exists(): shutil.copy2(p, target)
p = root / 'hragent-chat/src/App.vue'
t = p.read_text(encoding='utf-8-sig')
t = t.replace('showServices = false; createConversation()', 'createConversation()').replace('showServices = false; activeConversationId', 'activeConversationId')
t = '\n'.join(line for line in t.split('\n') if not any(marker in line for marker in ['@click="showServices = !showServices"', '<section v-if="showServices"', "import EmployeeServices from", "import { serviceRequest }", 'const showServices =', 'const requestServices =']))
t = t.replace('<section v-else class="chat-page">', '<section class="chat-page">')
p.write_text(t, encoding='utf-8')
tree = ast.parse((root / 'scripts/restore-original-pages.py').read_text(encoding='utf-8-sig'))
original = next(ast.literal_eval(n.value) for n in tree.body if isinstance(n, ast.Assign) and any(isinstance(t, ast.Name) and t.id == 'original_services' for t in n.targets))
(root / 'hragentv1/frontend/src/views/EmployeeServicesView.vue').write_text(original, encoding='utf-8')
print('Restored original chat and service navigation; backups saved.')

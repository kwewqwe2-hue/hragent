from pathlib import Path
import shutil
root=Path(__file__).resolve().parents[1]
def edit(relative, transform):
 p=root/relative;old=p.read_text(encoding='utf-8-sig');new=transform(old)
 if old==new:return
 backup=root/'.backups/care-functional'/relative
 backup.parent.mkdir(parents=True,exist_ok=True)
 if not backup.exists():shutil.copy2(p,backup)
 p.write_text(new,encoding='utf-8')
def relations(t):
 t=t.replace("<template v-if=\"mode === 'care'\">", "<template v-if=\"['care','onboarding','support'].includes(mode)\">")
 t=t.replace('从第一天开始，有人与你同行。', "{{ mode === 'support' ? '支持与关怀咨询' : '入职与成长导航' }}")
 t=t.replace('不熟悉流程很正常。按自己的节奏准备，有需要时找得到帮助。', "{{ mode === 'support' ? '整理当下的困扰，找到可行的下一步和实际支持渠道。' : '按阶段准备，展开每项任务查看操作步骤。完成进度会保存到你的账号。' }}")
 t=t.replace('<div class="er-switch"><button v-for="p in [\'入职导航\',\'压力与关怀\',\'离职准备\']"', '<div v-if="mode !== \'support\'" class="er-switch"><button v-for="p in [\'入职导航\',\'离职准备\']"')
 start=t.index('        <label v-for="task in visibleTasks"');end=t.index('        <article v-else class="er-card">',start)
 t=t[:start]+'''        <label v-if="careTab === '入职导航'" class="er-phase-filter">查看阶段 <select v-model="phase"><option>全部阶段</option><option>第一天</option><option>第一周</option><option>第一个月</option></select></label>
        <article v-for="task in visibleTasks" :key="task.key" class="er-task" :class="{ done: task.done }"><div class="er-task-content"><label class="er-task-heading"><input type="checkbox" :checked="task.done" :disabled="busy" @change="toggleTask(task)" /><span><small>{{ task.phase }}<span v-if="task.dueDate"> · 建议 {{ task.dueDate }}</span></small><strong>{{ task.title }}</strong></span></label><p>{{ task.detail }}</p><details><summary>展开行动指引</summary><ol><li v-for="step in (journeyGuidance[task.key]?.steps || [task.detail])" :key="step">{{ step }}</li></ol><a v-if="journeyGuidance[task.key] && task.key !== 'week-culture'" :href="workbenchUrl + journeyGuidance[task.key]!.path">{{ journeyGuidance[task.key]!.action }} →</a><p class="er-muted">按实际准备情况勾选完成；展开指引不会自动完成任务。</p></details></div></article>
        <WorkplaceTools v-if="careTab === '入职导航'" :request="props.request" />
'''+t[end:]
 t=t.replace('      <template v-else>\n        <article class="er-care-message">', '      <template v-else>\n        <CareWorkspace :request="props.request" />\n        <article class="er-care-message">',1)
 t=t.replace("import { computed, onMounted, reactive, ref } from 'vue'", "import { computed, onMounted, reactive, ref } from 'vue'\nimport WorkplaceTools from './WorkplaceTools.vue'\nimport CareWorkspace from './CareWorkspace.vue'\nimport { journeyGuidance } from './journeyGuidance'")
 t=t.replace("careTab = ref('入职导航')", "careTab = ref(props.mode === 'support' ? '压力与关怀' : '入职导航')")
 t=t.replace("const visibleTasks = computed(() => tasks.value.filter(t => (t.phase === '离职准备') === (careTab.value === '离职准备')))", "const phase = ref('全部阶段')\nconst visibleTasks = computed(() => tasks.value.filter(t => (t.phase === '离职准备') === (careTab.value === '离职准备')).filter(t => careTab.value === '离职准备' || phase.value === '全部阶段' || t.phase === phase.value))")
 t=t.replace('<style scoped>', '<style scoped>\n.er-task-content{width:100%}.er-task-heading{display:flex;gap:14px;align-items:center;cursor:pointer}.er-task-heading strong{display:block}.er-task details{margin-top:12px}.er-task li{line-height:1.9;margin:8px 0;color:#526b5e}.er-task summary{cursor:pointer;color:#257958}.er-phase-filter{display:flex;gap:12px;align-items:center;margin:18px 0}.er-phase-filter select{padding:8px;border:1px solid #cfddd4;border-radius:8px;background:white}\n')
 return t
def services(t):
 t=t.replace("{ key: 'care', label: '入职与关怀' }", "{ key: 'onboarding', label: '入职与成长' }, { key: 'support', label: '支持与关怀' }")
 t=t.replace("['care', 'compliance', 'operations'].includes(tab)", "['onboarding', 'support', 'compliance', 'operations'].includes(tab)")
 t=t.replace("ref(props.initialTab || 'policy')", "ref(props.initialTab === 'care' ? 'onboarding' : props.initialTab || 'policy')")
 t=t.replace("import { computed, onBeforeUnmount, onMounted, reactive, ref }", "import { computed, onBeforeUnmount, onMounted, reactive, ref, watch }")
 t=t.replace("const profile = ref<Profile>()", "watch(() => props.initialTab, value => { if (value) tab.value = value === 'care' ? 'onboarding' : value })\nconst profile = ref<Profile>()")
 return t
for folder in ['hragentv1/frontend/src/components','hragent-chat/src/components']:
 edit(folder+'/EmployeeRelations.vue', relations)
 edit(folder+'/EmployeeServices.vue',services)
for name in ['WorkplaceTools.vue','CareWorkspace.vue','journeyGuidance.ts']:
 shutil.copy2(root/'hragentv1/frontend/src/components'/name,root/'hragent-chat/src/components'/name)
edit('hragentv1/frontend/src/views/EmployeeExperienceView.vue',lambda t:t.replace("['policy','care','compliance'", "['policy','care','onboarding','support','compliance'"))
edit('hragentv1/frontend/src/views/EmployeeServicesView.vue',lambda t:t.replace("go('/employee-experience?section=care')",'go(item.path)').replace("icon: Postcard }", "icon: Postcard, path: '/employee-experience?section=onboarding' }").replace("icon: ChatDotRound }", "icon: ChatDotRound, path: '/employee-experience?section=support' }").replace('获取新人任务、协作工具与职场文化指引','按阶段完成新人任务、理解术语并准备工具权限').replace('获得压力疏导与企业支持资源的咨询入口','整理困扰、咨询助手并查找企业支持渠道'))
print('Distinct onboarding/support entry points and interactive guidance installed.')

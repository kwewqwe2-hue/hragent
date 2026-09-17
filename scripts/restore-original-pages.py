from pathlib import Path
import shutil
root = Path(__file__).resolve().parents[1]
backup = root / '.backups/before-page-restoration'
def change(relative, transform):
    path = root / relative
    original = path.read_text(encoding='utf-8-sig')
    revised = transform(original)
    if revised != original:
        target = backup / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        if not target.exists(): shutil.copy2(path, target)
        path.write_text(revised, encoding='utf-8')
def chat(t):
    t = t.replace('@click="createConversation(); servicesOpen = false"', '@click="createConversation"')
    t = t.replace('; servicesOpen = false"', '"')
    start = t.index('      <button class="new-chat-button" type="button" @click="servicesOpen')
    end = t.index('      <div class="history-label">', start)
    t = t[:start] + t[end:]
    start = t.index('    <section v-if="servicesOpen"')
    end = t.index('      <header class="chat-header">', start)
    t = t[:start] + '    <section class="chat-page">\n' + t[end:]
    start = t.index("import EmployeeServices from './components/EmployeeServices.vue'")
    end = t.index("const AUTH_KEY =", start)
    t = t[:start] + t[end:]
    start = t.index("const serviceLabel = ref(")
    end = t.index('const session =', start)
    t = t[:start] + t[end:]
    t = t.replace("{{ sending ? '正在处理' : serviceLabel }}", "{{ sending ? '正在处理' : '在线' }}")
    t = t.replace('    void loadServiceStatus()\n', '')
    t = t.replace("['查询我的年假余额', '出差住宿标准是什么', '查看我的提醒与待办']", "['查询我的年假余额', '我要申请年假', '查看我的请假进度']")
    return t
change('hragent-chat/src/App.vue', chat)
def css(t):
    t = t.replace('min-width: 320px;', 'min-width: 1024px;', 1)
    t = t.replace('grid-template-rows: auto auto auto auto minmax(0, 1fr) auto;', 'grid-template-rows: auto auto auto minmax(0, 1fr) auto;')
    start = t.index('  .app-shell {', t.index('@media (max-width: 700px)'))
    end = t.index('  .quick-board-panel {', start)
    return t[:start] + t[end:]
change('hragent-chat/src/styles.css', css)
def knowledge(t):
    t = t.replace('维护制度原文、适用人群与生效日期，为员工精准问答提供依据。', '公司制度文档由 n8n RAG 统一索引，SaaS 保存文档元数据。')
    t = t.replace('        <el-table-column prop="effectiveFrom" label="生效日期" width="130" />\n', '')
    start = t.index('        <el-alert title="精准问答')
    end = t.index('        <el-row :gutter="12">\n          <el-col :span="12">\n            <el-form-item label="来源">', start)
    t = t[:start] + t[end:]
    t = t.replace("  jobGrades: '', workTypes: '', legalEntities: '', sourceUrl: '', effectiveFrom: '', effectiveTo: '',\n", '')
    return t
change('hragentv1/frontend/src/views/KnowledgeView.vue', knowledge)
original_services = '''<template><div class="services-page"><div class="page-title"><div><h1>员工服务中心</h1><p>新增智能服务统一入口，既有请假、证明、通讯录入口保持不变。</p></div></div><section><div class="section-heading"><h2>日常服务</h2><span>制度政策精准问答</span></div><button class="service-card" type="button" @click="go('/assistant')"><el-icon><ChatDotRound /></el-icon><strong>Policy Copilot</strong><span>咨询考勤、年假、加班、差旅和福利等制度问题。</span><el-icon class="arrow"><ArrowRight /></el-icon></button></section><section><div class="section-heading"><h2>个人成长与关怀</h2><span>入职导航、成长支持与关怀咨询</span></div><div class="service-grid"><button v-for="item in care" :key="item.title" class="service-card" type="button" @click="go('/assistant')"><el-icon><component :is="item.icon" /></el-icon><strong>{{ item.title }}</strong><span>{{ item.description }}</span><el-icon class="arrow"><ArrowRight /></el-icon></button></div></section><section><div class="section-heading"><h2>合规与申诉</h2><span>敏感问题优先引导人工处理</span></div><button class="service-card" type="button" @click="go('/assistant')"><el-icon><WarningFilled /></el-icon><strong>合规咨询与人工转介</strong><span>咨询劳动关系、竞业、工伤等敏感问题；风险内容将转入人工处理流程。</span><el-icon class="arrow"><ArrowRight /></el-icon></button></section></div></template>
<script setup lang="ts">
import { ArrowRight, ChatDotRound, Postcard, WarningFilled } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
const router = useRouter()
const go = (path: string) => router.push(path)
const care = [
  { title: '入职与成长导航', description: '获取新人任务、协作工具与职场文化指引', icon: Postcard },
  { title: '支持与关怀咨询', description: '获得压力疏导与企业支持资源的咨询入口', icon: ChatDotRound }
]
</script>
<style scoped>.services-page section{margin-top:28px}.section-heading{display:flex;align-items:baseline;gap:12px;margin-bottom:12px}.section-heading h2{margin:0;color:#182230;font-size:18px}.section-heading span{color:#667085;font-size:13px}.service-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}.service-card{position:relative;width:100%;min-height:136px;display:flex;align-items:flex-start;flex-direction:column;gap:10px;padding:18px;color:#344054;background:#fff;border:1px solid #d9e0ea;border-radius:6px;text-align:left;cursor:pointer}.service-card:hover{border-color:#2f80ed;box-shadow:0 4px 12px rgba(47,128,237,.12)}.service-card>.el-icon:first-child{color:#2f80ed;font-size:22px}.service-card strong{font-size:15px}.service-card span{color:#667085;font-size:13px;line-height:1.55}.arrow{position:absolute;right:16px;bottom:16px;color:#2f80ed}@media(max-width:700px){.service-grid{grid-template-columns:1fr}.section-heading{align-items:flex-start;flex-direction:column;gap:3px}}</style>
'''
change('hragentv1/frontend/src/views/EmployeeServicesView.vue', lambda _: original_services)
print('Restored prior chat, knowledge and service page layouts; preserved a backup of the newer pages.')

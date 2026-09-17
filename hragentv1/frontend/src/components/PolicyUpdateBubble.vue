<template>
  <aside class="policy-update" aria-label="官方知识更新进度">
    <button class="bubble" :aria-expanded="expanded" @click="expanded = !expanded"><span class="mascot">🌱</span><span><strong>Kaka · 知识更新小助手</strong><small aria-live="polite">{{ summary }}</small></span><span>{{ expanded ? '收起' : '查看进度' }}</span></button>
    <div v-if="expanded" class="progress-detail">
      <p>按截至 {{ status?.asOf || '今天' }} 的有效政策持续核查，保留法规原始年份。公司制度由企业维护，官网更新不会覆盖公司文件。</p>
      <p v-if="publicCount !== undefined">国家与地方政策图谱已收录 {{ publicCount }} 条内容，包括官网定期采集及逐份核验补充的文件。采集记录数量不等于图谱总数。</p>
      <p v-if="error" role="alert">{{ error }}</p>
      <template v-if="status">
        <p>{{ status.schedule }} · {{ status.enabled ? '自动检查已开启' : '自动检查未开启' }}（服务运行时）</p>
        <p v-if="status.running">{{ status.progress.source || '正在准备官网检查' }} · {{ status.progress.phase === 'READING' ? '正在读取与比对正文' : '正在寻找政策文件' }}</p>
        <progress :value="status.progress.sourcesFinished" :max="status.progress.sourcesTotal" aria-label="来源检查进度" />
        <p>本轮已检查 {{ status.progress.sourcesFinished }}/{{ status.progress.sourcesTotal }} 个来源 · 已处理 {{ status.progress.documentsChecked }}/{{ status.progress.documentsTotal }} 份文件</p>
        <p v-if="status.progress.document">当前文件：{{ status.progress.document }}</p>
        <p>本知识库：{{ status.pendingCount }} 条候选待核验 · {{ status.approvedCount }} 条采集记录已核验入库</p>
        <p v-if="status.progress.finishedAt">本轮结束：{{ time(status.progress.finishedAt) }}（各来源成功情况见下方）</p>
        <div v-for="source in status.sources" :key="source.id" class="source-row"><a :href="source.url" target="_blank" rel="noopener noreferrer">{{ source.name }} ↗</a><small>最近检查：{{ time(check(source.id)?.checkedAt) }} · 完整成功：{{ time(check(source.id)?.successfulAt) }}</small><span v-if="check(source.id)?.error" class="warning">{{ check(source.id)?.error }}</span><span v-else-if="!check(source.id)">尚未完成检查</span></div>
        <p>{{ status.scope }} 新文件先更新到候选库，核实生效时间与适用范围后进入问答；检查结束不等于所有法规已核验为最新。</p>
        <a href="https://flk.npc.gov.cn/" target="_blank" rel="noopener noreferrer">国家法律法规数据库 · 人工核验入口 ↗</a>
      </template>
      <div class="actions"><el-button :loading="loading" @click="load">刷新进度</el-button><el-button v-if="isHr" type="primary" :disabled="status?.running" :loading="starting" @click="start">立即检查官网</el-button></div>
    </div>
  </aside>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { getData, postData } from '../api/http'
defineProps<{ isHr: boolean; publicCount?: number }>()
const emit = defineEmits<{ updated: [] }>()
type Check = { id: string; checkedAt?: string; successfulAt?: string; error?: string }
type Status = { enabled: boolean; running: boolean; schedule: string; scope: string; asOf: string; pendingCount: number; approvedCount: number; sources: { id: string; name: string; url: string }[]; checks: Check[]; progress: { phase: string; source: string; document: string; sourcesFinished: number; sourcesTotal: number; documentsChecked: number; documentsTotal: number; finishedAt: string } }
const status = ref<Status>(), expanded = ref(false), error = ref(''), loading = ref(false), starting = ref(false)
const summary = computed(() => error.value ? '暂时没有获取到最新进度，点这里重试' : !status.value ? '正在获取官方知识更新状态…' : status.value.running ? `正在检查官网 · ${status.value.progress.sourcesFinished}/${status.value.progress.sourcesTotal} 个来源` : `${status.value.pendingCount} 条待核验${status.value.checks.some(c => c.error) ? ' · 部分来源需要重试' : ' · 查看最近检查结果'}`)
const check = (id: string) => status.value?.checks.find(c => c.id === id)
const time = (value?: string) => value ? value.replace('T',' ').slice(0,19) : '暂无记录'
let timer: number | undefined, disposed = false
async function load() {
  if (loading.value || disposed) return
  loading.value = true
  try { const next = await getData<Status>('/policy-monitor/progress'); if (disposed) return; if (status.value && status.value.approvedCount !== next.approvedCount) emit('updated'); status.value = next; error.value = '' }
  catch { if (!disposed) error.value = '更新进度暂时无法获取，请稍后重试。' }
  finally { loading.value = false }
}
async function start() { starting.value = true; try { await postData('/admin/policy-monitor/scan', {}); await load() } catch { error.value = '未能启动检查，请重试。' } finally { starting.value = false } }
const refresh = () => { if (document.visibilityState === 'visible') void load() }
onMounted(() => { void load(); timer = window.setInterval(refresh,5000); document.addEventListener('visibilitychange',refresh) })
onBeforeUnmount(() => { disposed = true; clearInterval(timer); document.removeEventListener('visibilitychange',refresh) })
</script>
<style scoped>
.policy-update{margin:24px 0;border:1px solid #c7ddd2;border-radius:18px;background:#f4faf6;color:#35564a;overflow:hidden}.bubble{border:0;background:transparent;color:inherit;padding:20px;display:flex;gap:15px;align-items:center;width:100%;text-align:left;cursor:pointer}.bubble>span:nth-child(2){flex:1;display:grid;gap:8px}.bubble small{font-size:13px;color:#668375}.bubble>span:last-child{font-size:12px}.mascot{font-size:26px}.progress-detail{border-top:1px solid #d7e7de;padding:20px;font-size:13px;line-height:1.8}.progress-detail progress{width:100%;accent-color:#2d8b68}.source-row{padding:12px 0;border-bottom:1px solid #dae8e0;display:grid;gap:5px}.source-row small{color:#718376}.progress-detail a{color:#286985}.warning{color:#986425;overflow-wrap:anywhere}.actions{display:flex;gap:10px;margin-top:16px}
</style>

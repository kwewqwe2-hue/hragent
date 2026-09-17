<template>
  <Teleport to="body">
    <aside v-if="unread && !dismissed && !opened" class="approval-toast" role="status" aria-label="Kaka 请假消息提醒">
      <img class="kaka-notifier" :src="assetBase + 'kaka-3D.png'" alt="Kaka" />
      <small class="kaka-label">Kaka · 有新进度</small>
      <button class="dismiss" aria-label="收起请假提醒" @click="dismissNotifications"><X :size="17" /></button>
      <strong>{{ pendingUnread ? `你有 ${pendingUnread} 份请假申请待审核` : `你的请假有 ${mineUnread} 条新动态` }}</strong>
      <p>{{ pendingUnread ? '查看申请内容和材料，在这里完成审核。' : '查看主管、HR 的审核结果和回复。' }}</p>
      <button class="primary" @click="openInbox(pendingUnread ? 'pending' : 'mine')">{{ pendingUnread ? '立即审核' : '查看结果' }}</button>
    </aside>
    <dialog ref="dialog" class="approval-dialog" aria-labelledby="approval-title" @close="opened = false" @cancel="onCancel">
      <header><div><h2 id="approval-title">请假消息与审批</h2><p>申请、审核意见和结果在这里同步</p></div><button aria-label="关闭请假审批" :disabled="submitting" @click="closeInbox"><X :size="22" /></button></header>
      <nav aria-label="请假消息分类">
        <button v-if="reviewer" :class="{ active: tab === 'pending' }" :disabled="submitting" @click="selectTab('pending')">{{ role === 'HR' ? 'HR 待审核' : '主管待审批' }}（{{ pending.length }}）</button>
        <button :class="{ active: tab === 'mine' }" :disabled="submitting" @click="selectTab('mine')">我的请假进度</button>
        <button class="refresh" :disabled="loading || submitting" @click="load">刷新</button>
      </nav>
      <div class="approval-content">
        <p v-if="syncError" class="error" role="alert">{{ syncError }} <button @click="load">重新同步</button></p>
        <p v-if="actionError" class="error" role="alert">{{ actionError }}</p><p v-if="success" class="success" role="status">{{ success }}</p>
        <p v-if="!ready">正在加载请假记录…</p><p v-else-if="!visibleRows.length" class="empty">{{ tab === 'pending' ? '当前没有待审核的请假申请。' : '你还没有提交请假申请。' }}</p>
        <article v-for="row in visibleRows" :key="row.id" class="leave-card" :data-leave-id="row.id">
          <div class="card-heading"><strong>{{ tab === 'pending' ? row.employeeName + ' · ' : '' }}{{ row.leaveTypeLabel }} · {{ row.days }} 天</strong><span :class="['status', row.status.toLowerCase()]">{{ row.statusLabel }}</span></div>
          <p class="dates">{{ row.startDate }} 至 {{ row.endDate }} <small>#{{ row.id }}</small></p><p><b>申请说明：</b>{{ row.reason }}</p>
          <p v-if="row.managerOpinion"><b>主管回复：</b>{{ row.managerOpinion }}</p><p v-if="row.hrOpinion"><b>HR 回复：</b>{{ row.hrOpinion }}</p>
          <p v-if="row.status === 'PENDING_HR'" class="stage">{{ row.managerReviewedAt ? '主管已通过，等待 HR 审核。' : '申请已提交，等待 HR 审核。' }}</p>
          <p v-if="row.status === 'APPROVED'" class="stage">审核已完成，假期余额和日历已更新。</p>
          <details v-if="tab === 'pending' && (row.aiSummary || row.aiEvidence)"><summary>查看辅助信息与制度依据</summary><p>{{ row.aiSummary }}</p><p>{{ row.aiEvidence }}</p></details>
          <button v-if="row.medicalRecordId" :disabled="downloadBusy || submitting" @click="downloadMedical(row)">查看病假原始材料</button>
          <div v-if="tab === 'pending' && selected?.id !== row.id" class="card-actions"><button class="primary" :disabled="submitting || !!syncError" @click="choose(row, true)">{{ role === 'HR' ? '审核通过' : '通过申请' }}</button><button class="reject" :disabled="submitting || !!syncError" @click="choose(row, false)">驳回申请</button></div>
          <form v-if="selected?.id === row.id" class="review-form" @submit.prevent="submitReview">
            <strong>{{ approved ? (role === 'HR' ? '确认通过并完成备案' : '确认通过，交 HR 审核') : '确认驳回申请' }}</strong>
            <p>{{ approved ? (role === 'HR' ? '通过后更新假期余额和日历，员工会收到审核结果。' : '通过后提醒 HR 接续审核，员工可看到当前进度。') : '驳回后本次申请结束，员工会收到你的意见。' }}</p>
            <label :for="`review-opinion-${row.id}`">{{ role === 'HR' ? 'HR 审核意见' : '主管审批意见' }}</label><textarea :id="`review-opinion-${row.id}`" v-model="opinion" maxlength="600" rows="3" required :disabled="submitting" placeholder="填写处理意见，让员工了解结果和下一步" />
            <div class="card-actions"><button :class="approved ? 'primary' : 'reject'" type="submit" :disabled="submitting || !opinion.trim() || !!syncError">{{ submitting ? '正在提交…' : approved ? '确认通过' : '确认驳回' }}</button><button type="button" :disabled="submitting" @click="selected = null">取消</button></div>
          </form>
        </article>
      </div>
    </dialog>
  </Teleport>
</template>
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { X } from 'lucide-vue-next'
import { apiErrorMessage, serviceRequest } from '../api'
import type { AuthSession } from '../types'
const props = defineProps<{ session: AuthSession }>()
const assetBase = import.meta.env.BASE_URL
type Row = { id: number; employeeName: string; leaveTypeLabel: string; days: number; reason: string; status: string; statusLabel: string; startDate: string; endDate: string; managerOpinion?: string; hrOpinion?: string; managerReviewedAt?: string; hrRecordedAt?: string; medicalRecordId?: number; aiSummary?: string; aiEvidence?: string }
type Tab = 'pending' | 'mine'
const role = computed(() => props.session.user.role), reviewer = computed(() => role.value === 'MANAGER' || role.value === 'HR')
const mine = ref<Row[]>([]), pending = ref<Row[]>([]), ready = ref(false), loading = ref(false)
const dialog = ref<HTMLDialogElement>(), opened = ref(false), tab = ref<Tab>('mine'), dismissed = ref(false)
const syncError = ref(''), actionError = ref(''), success = ref(''), selected = ref<Row | null>(null)
const approved = ref(true), opinion = ref(''), submitting = ref(false), downloadBusy = ref(false), seen = ref<Record<string, string>>({})
let alive = true, epoch = 0, loadVersion = 0, observed = '', timer: ReturnType<typeof setInterval> | undefined
let toastTimer: ReturnType<typeof setTimeout> | undefined
const scope = computed(() => `${props.session.workspaceId ?? props.session.user.tenantId}:${props.session.user.id}:${props.session.user.employeeProfileId ?? ''}:${role.value}`)
const storageKey = computed(() => `hragent_approval_seen_v1:${scope.value}`)
// Read receipts contain only request status and timestamps, never reasons or medical content.
const signature = (r: Row) => JSON.stringify([r.status, r.managerReviewedAt, r.hrRecordedAt])
const pendingUnread = computed(() => pending.value.filter(r => seen.value[`pending:${r.id}`] !== signature(r)).length)
const mineUnread = computed(() => mine.value.filter(r => seen.value[`mine:${r.id}`] !== signature(r)).length)
const unread = computed(() => pendingUnread.value + mineUnread.value), visibleRows = computed(() => tab.value === 'pending' ? pending.value : mine.value)
function restoreSeen() { try { const stored = JSON.parse(localStorage.getItem(storageKey.value) || '{}'); seen.value = stored && typeof stored === 'object' && !Array.isArray(stored) ? stored : {} } catch { seen.value = {} } }
function persistSeen() { try { localStorage.setItem(storageKey.value, JSON.stringify(seen.value)) } catch { /* keep in-memory receipts */ } }
function dismissNotifications() {
  dismissed.value = true; clearTimeout(toastTimer)
  for (const row of mine.value) seen.value[`mine:${row.id}`] = signature(row)
  for (const row of pending.value) seen.value[`pending:${row.id}`] = signature(row)
  persistSeen()
}
function markRead() {
  if (!ready.value) return
  for (const row of visibleRows.value) seen.value[`${tab.value}:${row.id}`] = signature(row)
  const keys = new Set([...pending.value.map(r => `pending:${r.id}`), ...mine.value.map(r => `mine:${r.id}`)])
  seen.value = Object.fromEntries(Object.entries(seen.value).filter(([key]) => keys.has(key)))
  try { localStorage.setItem(storageKey.value, JSON.stringify(seen.value)) } catch { /* keep in-memory receipts */ }
}
async function load() {
  if (!alive || loading.value || submitting.value || document.hidden) return
  const current = props.session, currentEpoch = epoch, version = ++loadVersion, currentRole = role.value
  loading.value = true
  try {
    const [own, queue] = await Promise.all([serviceRequest(current, '/leave/my') as Promise<Row[]>, currentRole === 'MANAGER' || currentRole === 'HR' ? serviceRequest(current, `/leave/${currentRole === 'HR' ? 'hr' : 'manager'}/pending`) as Promise<Row[]> : Promise.resolve([] as Row[])])
    if (!alive || currentEpoch !== epoch || version !== loadVersion) return
    // First visit establishes a baseline instead of announcing every historical employee record.
    if (!ready.value && !Object.keys(seen.value).length) { for (const row of own) seen.value[`mine:${row.id}`] = signature(row); persistSeen() }
    mine.value = own; pending.value = queue; ready.value = true; syncError.value = ''
    const changes = JSON.stringify([queue.map(r => [r.id, signature(r)]), own.map(r => [r.id, signature(r)])])
    if (changes !== observed) { dismissed.value = false; observed = changes; clearTimeout(toastTimer); if (unread.value) toastTimer = setTimeout(dismissNotifications, 12000) }
    if (selected.value && !queue.some(r => r.id === selected.value?.id)) { selected.value = null; actionError.value = '这份申请已由其他审核人员处理，待办已更新。' }
    if (opened.value) markRead()
  } catch (error) { if (alive && currentEpoch === epoch && version === loadVersion) syncError.value = apiErrorMessage(error) }
  finally { if (alive && currentEpoch === epoch && version === loadVersion) loading.value = false }
}
async function openInbox(target?: Tab) {
  clearTimeout(toastTimer)
  tab.value = reviewer.value ? (target || 'pending') : 'mine'; opened.value = true; dismissed.value = true; actionError.value = ''; success.value = ''
  await nextTick(); if (!alive) return
  if (!dialog.value?.open) dialog.value?.showModal()
  markRead(); void load()
}
function closeInbox() { if (!submitting.value) { selected.value = null; dialog.value?.close() } }
function onCancel(event: Event) { if (submitting.value) event.preventDefault(); else selected.value = null }
function selectTab(value: Tab) { tab.value = value; selected.value = null; actionError.value = ''; success.value = ''; markRead() }
function choose(row: Row, decision: boolean) { selected.value = row; approved.value = decision; actionError.value = ''; success.value = ''; opinion.value = decision ? (role.value === 'HR' ? '审核通过，完成备案。' : '同意，提交 HR 审核。') : ''; void nextTick(() => document.getElementById(`review-opinion-${row.id}`)?.focus()) }
async function submitReview() {
  if (!selected.value || !opinion.value.trim() || submitting.value || syncError.value) return
  const row = selected.value, current = props.session, currentEpoch = epoch, currentRole = role.value
  if (currentRole !== 'HR' && currentRole !== 'MANAGER') return
  submitting.value = true; actionError.value = ''; ++loadVersion; loading.value = false
  try {
    const path = currentRole === 'HR' ? `/leave/hr/${row.id}/record` : `/leave/manager/${row.id}/review`
    const result = await serviceRequest(current, path, { method: 'PUT', body: { approved: approved.value, opinion: opinion.value.trim() } }) as Row
    if (!alive || currentEpoch !== epoch) return
    pending.value = pending.value.filter(r => r.id !== row.id); selected.value = null
    success.value = `申请 #${row.id} 已处理：${result.statusLabel}。${result.status === 'PENDING_HR' ? 'HR 将收到待审核提醒，员工可查看进度。' : '员工可在 Agent AI 查看审核结果和意见。'}`
  } catch (error) { if (alive && currentEpoch === epoch) actionError.value = apiErrorMessage(error) }
  finally { if (alive && currentEpoch === epoch) { submitting.value = false; void load() } }
}
async function downloadMedical(row: Row) {
  const current = props.session, currentEpoch = epoch; downloadBusy.value = true; actionError.value = ''
  try {
    const blob = await serviceRequest(current, `/leave/${row.id}/medical-record`, { binary: true }) as Blob
    if (!alive || currentEpoch !== epoch) return
    const url = URL.createObjectURL(blob), link = document.createElement('a'); link.href = url; link.download = `病假材料-${row.id}.${blob.type.includes('pdf') ? 'pdf' : blob.type.includes('png') ? 'png' : 'jpg'}`; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (error) { if (alive && currentEpoch === epoch) actionError.value = apiErrorMessage(error) }
  finally { if (alive && currentEpoch === epoch) downloadBusy.value = false }
}
function reset() { epoch++; loadVersion++; clearTimeout(toastTimer); dismissed.value = false; dialog.value?.close(); opened.value = false; loading.value = false; submitting.value = false; mine.value = []; pending.value = []; ready.value = false; selected.value = null; downloadBusy.value = false; syncError.value = ''; actionError.value = ''; success.value = ''; observed = ''; restoreSeen(); void load() }
watch(() => [scope.value, props.session.token], reset)
onMounted(() => { restoreSeen(); void load(); timer = setInterval(load, 5000); document.addEventListener('visibilitychange', load); window.addEventListener('focus', load) })
onBeforeUnmount(() => { alive = false; epoch++; dialog.value?.close(); clearInterval(timer); clearTimeout(toastTimer); document.removeEventListener('visibilitychange', load); window.removeEventListener('focus', load) })
defineExpose({ openInbox })
</script>
<style scoped>
.approval-toast .kaka-notifier{width:64px;height:76px;object-fit:contain;float:left;margin:0 12px 6px -6px}.kaka-label{display:block;color:#548477;margin-bottom:8px}.approval-toast{user-select:text}
.approval-updates{display:flex;align-items:center;gap:12px;flex:0 0 auto;padding:8px 24px;border-bottom:1px solid #e3ece8;background:#f7fbf9;color:#24534b;font-size:13px}.approval-updates button,.approval-dialog button,.approval-toast button{font:inherit;cursor:pointer;border:1px solid #dce7e2;border-radius:9px;background:white;color:#24534b;padding:8px 12px}.approval-updates .inbox-button{display:flex;align-items:center;gap:8px;background:transparent;border:0;padding:3px}.inbox-button b{font-size:12px;background:#d9eee3;border-radius:12px;padding:3px 8px}.approval-updates>span{margin-left:auto;color:#687d75;font-size:12px}.approval-toast{position:fixed;right:24px;bottom:26px;z-index:80;width:min(360px,calc(100vw - 32px));box-sizing:border-box;background:white;border:1px solid #d7e7df;box-shadow:0 12px 40px #14362a26;border-radius:16px;padding:22px;color:#213f34}.approval-toast strong{display:block;padding-right:20px}.approval-toast p{font-size:14px;color:#64776e;line-height:1.6}.approval-toast .dismiss{position:absolute;right:8px;top:8px;border:0;padding:4px}.approval-dialog{box-sizing:border-box;width:min(720px,calc(100vw - 24px));max-height:85dvh;border:1px solid #dce7e2;border-radius:20px;padding:0;color:#243c32;background:#fafcfb;box-shadow:0 20px 80px #15392a40}.approval-dialog::backdrop{background:#132d245c}.approval-dialog header{display:flex;justify-content:space-between;gap:12px;align-items:center;padding:20px 24px;background:white}.approval-dialog h2{margin:0;font-size:20px}.approval-dialog header p{margin:7px 0 0;color:#6c7f75;font-size:13px}.approval-dialog header button{border:0}.approval-dialog nav{display:flex;gap:8px;padding:12px 20px;border-block:1px solid #e3ece8;flex-wrap:wrap}.approval-dialog nav .active{background:#e3f1e9;border-color:#9fc8b3}.approval-dialog .refresh{margin-left:auto}.approval-content{padding:0 20px 20px}.leave-card{background:white;border:1px solid #e0e8e3;border-radius:14px;margin-top:14px;padding:18px;font-size:14px;overflow-wrap:anywhere}.card-heading{display:flex;gap:12px;align-items:center;justify-content:space-between}.status{font-size:12px;white-space:nowrap;background:#f0f3f1;padding:4px 8px;border-radius:8px}.status.approved{color:#18754e;background:#e3f4ea}.status.rejected{color:#a53a3a;background:#fff0ed}.dates{color:#596f63}.dates small{margin-left:8px}.leave-card p{line-height:1.65;margin:9px 0;white-space:pre-wrap}.leave-card details{margin:12px 0}.leave-card summary{cursor:pointer;color:#4c7060}.card-actions{display:flex;gap:10px;margin-top:14px;flex-wrap:wrap}.approval-dialog .primary,.approval-toast .primary{background:#21684e;color:white;border-color:#21684e}.approval-dialog .reject{background:#fff5f3;color:#a63d36;border-color:#efd1cd}.approval-dialog button:disabled{opacity:.5;cursor:not-allowed}.review-form{margin-top:16px;border-top:1px solid #dfe9e3;padding-top:14px}.review-form label{display:block;margin:12px 0 6px}.review-form textarea{box-sizing:border-box;width:100%;font:inherit;border:1px solid #b6cec0;border-radius:9px;padding:10px;resize:vertical}.error{color:#a22f29;background:#fff1ef;padding:12px;border-radius:9px}.success{color:#1e6b49;background:#e8f5ec;padding:12px;border-radius:9px}.stage{color:#56715f;font-size:13px}.empty{padding:36px 10px;text-align:center;color:#718278}@media(max-width:600px){.approval-updates{padding:8px 12px}.approval-updates>span{display:none}.approval-toast{right:16px;bottom:16px}.approval-dialog header{padding:16px}.approval-dialog nav{padding:10px}.approval-content{padding:0 10px 12px}.leave-card{padding:13px}.card-heading{align-items:flex-start}.approval-dialog nav button{font-size:13px}.approval-dialog{max-height:90dvh}}
</style>

<template>
  <section class="pulse-panel">
    <span class="pulse-eyebrow">让工作体验更好一点</span>
    <h3>员工体验反馈</h3>
    <p>用五个小问题，分享你对工作安排、沟通协作和组织支持的感受。</p>
    <p v-if="error" role="alert" class="pulse-error">{{ error }}</p>
    <template v-if="admin">
      <label>查看月份 <input v-model="period" type="month" @change="loadResults" /></label>
      <button :disabled="busy" @click="loadResults">刷新汇总</button>
      <p v-if="results" role="status">{{ results.message }}</p>
      <template v-if="results?.available">
        <p>{{ results.participants }} 人自愿参与 · 评分范围 1–5 分</p>
        <div v-for="metric in results.metrics" :key="metric.title" class="pulse-metric"><span>{{ metric.title }}</span><strong>{{ metric.average.toFixed(1) }} / 5</strong><meter min="1" max="5" :value="metric.average" :aria-label="metric.title" /></div>
        <p>下一步：选出最需要改善的一项，与团队讨论具体障碍、负责人和复盘时间。</p>
      </template>
    </template>
    <template v-else>
      <p class="pulse-note">自愿参与，每月一次。答案加密保存在本系统，不向 HR 展示个人答案。每月结束后，至少 5 人参与才展示企业整体汇总。账号仅用于生成防重复标记，不记录姓名和部门；这不代表对系统管理员完全匿名。提交后本期不可修改。</p>
      <p v-if="loading" role="status">正在读取本期反馈…</p>
      <p v-else-if="submitted" class="pulse-thanks" role="status">感谢你的分享，{{ period }} 的反馈已保存。</p>
      <template v-else-if="model">
        <label class="pulse-consent"><input v-model="consent" type="checkbox" :disabled="busy" />我了解以上用途，愿意参与 {{ period }} 的体验反馈</label>
        <SurveyComponent :model="model" />
        <button class="pulse-submit" :disabled="busy || !consent" @click="submit">{{ busy ? '正在保存…' : '提交体验反馈' }}</button>
      </template>
    </template>
  </section>
</template>
<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, shallowRef } from 'vue'
import { Model } from 'survey-core'
import { SurveyComponent } from 'survey-vue3-ui'
import 'survey-core/survey-core.fontless.min.css'
const props = defineProps<{ request: (path: string, options?: { method?: string; body?: unknown }) => Promise<any>; admin?: boolean }>()
const model = shallowRef<Model>(), period = ref(''), submitted = ref(false), consent = ref(false), busy = ref(false), loading = ref(true), error = ref('')
const results = ref<{ available: boolean; participants?: number; message: string; metrics: { title: string; average: number }[] }>()
const endpoint = '/employee-relations/pulse'
async function loadResults() {
  if (!period.value || busy.value) return
  busy.value = true; error.value = ''; results.value = undefined
  try { results.value = await props.request(endpoint + '/results?period=' + encodeURIComponent(period.value)) }
  catch (e: any) { error.value = e.response?.data?.message || e.message || '读取失败，请重试' }
  finally { busy.value = false }
}
async function submit() {
  if (busy.value || !consent.value || !model.value || !model.value.validate()) return
  busy.value = true; error.value = ''; model.value.mode = 'display'
  try { await props.request(endpoint, { method: 'POST', body: { period: period.value, consent: consent.value, answers: model.value.data } }); submitted.value = true }
  catch (e: any) { error.value = e.response?.data?.message || e.message || '保存失败，答案仍在，可重试' }
  finally { busy.value = false; if (model.value) model.value.mode = 'edit' }
}
onMounted(async () => {
  try {
    const current = await props.request(endpoint); period.value = current.period; submitted.value = current.submitted
    if (props.admin) { const [y, m] = period.value.split('-').map(Number); period.value = `${m === 1 ? y! - 1 : y}-${String(m === 1 ? 12 : m! - 1).padStart(2, '0')}`; await loadResults(); return }
    const survey = new Model({ showQuestionNumbers: false, showNavigationButtons: false, widthMode: 'responsive', requiredText: '', questionErrorLocation: 'bottom', elements: current.questions.map((q: { name: string; title: string }) => ({ type: 'radiogroup', name: q.name, title: q.title, isRequired: true, requiredErrorText: '请选择符合你体验的一项', choices: [{ value: 1, text: '很不认同' }, { value: 2, text: '不太认同' }, { value: 3, text: '一般' }, { value: 4, text: '比较认同' }, { value: 5, text: '很认同' }] })) })
    survey.applyTheme({ cssVariables: { '--sjs-primary-backcolor': '#227859', '--sjs-font-family': 'inherit', '--sjs-font-size': '15px', '--sjs-base-unit': '6px', '--sjs-general-backcolor': '#ffffff', '--sjs-general-backcolor-dim': '#f5f9f7' } })
    model.value = survey
  } catch (e: any) { error.value = e.response?.data?.message || e.message || '反馈服务暂不可用' }
  finally { loading.value = false }
})
onBeforeUnmount(() => model.value?.dispose())
</script>
<style scoped>
.pulse-panel{margin-top:24px;padding:24px;border:1px solid #dce8e1;border-radius:18px;background:#fff;color:#28443b;overflow:hidden}.pulse-panel h3{font-size:22px;margin:8px 0}.pulse-panel p{line-height:1.8}.pulse-eyebrow{color:#227859;font-size:13px;letter-spacing:1px}.pulse-note{font-size:13px;color:#667b72}.pulse-consent{display:flex;gap:10px;align-items:flex-start;margin:20px 0;line-height:1.7}.pulse-panel button{padding:10px 18px;border:1px solid #bad4c5;border-radius:9px;background:white;color:#227859;cursor:pointer}.pulse-panel button:disabled{opacity:.5;cursor:default}.pulse-panel .pulse-submit{background:#227859;color:white}.pulse-error{color:#b53232}.pulse-thanks{padding:18px;background:#edf7f0;border-radius:10px}.pulse-metric{display:grid;grid-template-columns:1fr auto;gap:10px;margin:24px 0}.pulse-metric meter{width:100%;grid-column:1/-1}.pulse-panel input[type=month]{padding:8px;max-width:100%;margin-right:10px}.pulse-panel :deep(.sd-root-modern){font-family:inherit}.pulse-panel :deep(.sd-body.sd-body--responsive){padding:0}.pulse-panel :deep(.sd-page){padding:8px 0}.pulse-panel :deep(.sd-question){min-width:0}@media(max-width:600px){.pulse-panel{padding:16px}.pulse-panel h3{font-size:20px}}
</style>

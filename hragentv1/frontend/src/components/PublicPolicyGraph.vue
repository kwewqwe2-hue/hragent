<template>
  <div class="public-graph">
    <div class="public-root"><strong>国家与地方政策</strong><span>按适用地区 → 政策主题 → 官方文件检索</span></div>
    <p>连线表示分类归属，不代表法规之间的授权、修订或替代关系。</p>
    <nav aria-label="法规适用地区"><button v-for="r in regions" :key="r" :aria-pressed="area === r" @click="area = r; selectedTopic = ''; limit = 8">{{ r }} <small>{{ groups.filter(g => g.region === r).length }}</small></button></nav>
    <div class="public-branches">
      <div class="topic-column"><button v-for="t in topics" :key="t.id" :aria-pressed="selectedTopic === t.id" @click="selectedTopic = t.id; limit = 8">{{ t.name }} <span>→</span></button></div>
      <div class="document-column">
        <span class="edge-label">{{ area }} · {{ activeTopicName }} · {{ documents.length }} 份依据</span>
        <button v-for="g in documents.slice(0, limit)" :key="g.id" class="law-node" @click="$emit('select', g)"><strong>{{ g.title }}</strong><span>{{ g.source }}</span><small>生效日期：{{ g.articles[0]?.effectiveFrom || '待确认' }} · {{ validity(g) }}</small></button>
        <el-button v-if="documents.length > limit" @click="limit += 8">展开更多依据</el-button>
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { knowledgeTopics, type PolicyGroup } from '../utils/knowledgeTopics'
const props = defineProps<{ groups: PolicyGroup[] }>()
defineEmits<{ select: [group: PolicyGroup] }>()
const area = ref(''), selectedTopic = ref(''), limit = ref(8)
const regions = computed(() => [...new Set(props.groups.map(g => g.region))].sort((a,b) => a === '全国' ? -1 : b === '全国' ? 1 : a.localeCompare(b)))
const regional = computed(() => props.groups.filter(g => g.region === area.value))
const topics = computed(() => knowledgeTopics.filter(t => regional.value.some(g => g.topics.includes(t.id))))
const activeTopic = computed(() => topics.value.some(t => t.id === selectedTopic.value) ? selectedTopic.value : topics.value[0]?.id)
const activeTopicName = computed(() => topics.value.find(t => t.id === activeTopic.value)?.name || '')
const documents = computed(() => regional.value.filter(g => g.topics.includes(activeTopic.value || '')))
watch(regions, value => { if (!value.includes(area.value)) area.value = value[0] || '' }, { immediate: true })
watch(() => props.groups, () => { limit.value = 8 })
function validity(g: PolicyGroup) {
  const a = g.articles[0], today = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai' }).format(new Date())
  if (!a || a.reviewStatus !== 'APPROVED') return '待核验'
  if (!a.effectiveFrom) return '生效时间待核验'
  if (a.effectiveFrom > today) return '尚未生效'
  if (a.effectiveTo && a.effectiveTo < today) return '历史版本'
  return '库内有效版本'
}
</script>
<style scoped>
.public-graph{padding:24px;background:#f2f6fe;border:1px solid #ccdaee;border-radius:16px;color:#294b75}.public-root{display:flex;flex-wrap:wrap;gap:12px;align-items:center;padding:20px;background:#244f84;color:white;border-radius:12px}.public-root span,.public-graph>p{font-size:12px;line-height:1.7}.public-graph nav{display:flex;gap:8px;flex-wrap:wrap;margin:20px 0}.public-graph button{cursor:pointer;font:inherit;text-align:left;border:1px solid #c6d7ec;background:white;color:#294b75;border-radius:9px;padding:12px}.public-graph button[aria-pressed=true]{background:#dbe8fc;border-color:#5589c5}.public-branches{display:grid;grid-template-columns:160px minmax(0,1fr);gap:28px}.topic-column{display:flex;flex-direction:column;gap:10px}.topic-column span{float:right}.document-column{border-left:2px solid #aec9e8;padding-left:24px;display:flex;flex-direction:column;gap:12px}.edge-label{font-size:12px}.law-node{position:relative;display:grid;gap:8px}.law-node:before{content:'';position:absolute;left:-25px;top:25px;width:24px;border-top:2px solid #aec9e8}.law-node span,.law-node small{font-size:12px;overflow-wrap:anywhere;color:#607c9b}@media(max-width:650px){.public-branches{grid-template-columns:1fr}.topic-column{flex-direction:row;flex-wrap:wrap}.public-graph{padding:14px}}
</style>

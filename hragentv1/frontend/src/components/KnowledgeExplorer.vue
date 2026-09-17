<template>
  <section class="knowledge-explorer" v-loading="loading">
    <div class="kb-domains" aria-label="知识库类型"><button v-for="d in domains" :key="d.id" :aria-pressed="domain === d.id" @click="domain = d.id; reset()"><strong>{{ d.title }}</strong><span>{{ d.hint }}</span><small>{{ articles.filter(a => policyDomain(a) === d.id).length }} 条内容</small></button></div>
    <div class="kb-search">
      <div><span class="kb-eyebrow">POLICY ATLAS</span><h2>从一个问题，找到相关制度</h2><p>按政策主题阅读完整内容，也可以沿着图谱探索关联。</p></div>
      <el-input v-model="query" clearable placeholder="搜索政策、关键词或正文，例如：年假、上海、报销" aria-label="搜索知识库" />
      <div class="kb-search-scope"><el-checkbox v-model="includeBody">同时搜索全文</el-checkbox><span>{{ includeBody ? '包含正文中提及的内容，按相关程度排序' : '匹配政策名称、条款标题和板块，减少无关内容' }}</span></div>
      <div class="kb-filters">
        <el-select v-model="region" aria-label="适用范围" placeholder="全部适用范围"><el-option label="全部适用范围" value="" /><el-option v-for="r in regions" :key="r" :label="r" :value="r" /></el-select>
        <el-select v-model="source" aria-label="制度来源" placeholder="全部来源"><el-option label="全部来源" value="" /><el-option v-for="s in sources" :key="s" :label="s" :value="s" /></el-select>
        <el-select v-model="validity" aria-label="版本状态" placeholder="全部版本"><el-option label="全部版本" value="" /><el-option label="当前适用" value="current" /><el-option label="待生效" value="future" /><el-option label="历史版本" value="historical" /><el-option label="待核验" value="unverified" /></el-select>
        <el-button @click="reset">重置筛选</el-button>
        <span>{{ filtered.length }} 项政策 · {{ populatedTopics.length }} 个板块</span>
      </div>
    </div>
    <div class="kb-layout">
      <nav class="kb-topics" aria-label="政策板块">
        <button :class="{ active: !topic }" @click="selectTopic('')"><strong>全部政策板块</strong><span>{{ matching.length }} 项政策</span></button>
        <button v-for="t in knowledgeTopics" :key="t.id" :class="{ active: topic === t.id }" @click="selectTopic(t.id)">
          <strong>{{ t.name }} <small>{{ count(t.id) }}</small></strong><span>{{ t.hint }}</span>
        </button>
      </nav>
      <div class="kb-main">
        <div class="kb-view-heading"><div><h3>{{ topicName }}</h3><p>{{ topic ? '同一政策的跨页内容已汇集，点击查看全文与出处。' : '选择一个板块，展开政策与关联主题。' }}</p></div><div class="kb-switch" aria-label="知识库视图"><button :aria-pressed="mode === 'graph'" @click="mode = 'graph'">知识图谱</button><button :aria-pressed="mode === 'cards'" @click="mode = 'cards'">分类阅读</button></div></div>
        <el-empty v-if="!loading && !filtered.length" :description="query.trim() ? '当前范围没有匹配的政策，可调整筛选或开启全文搜索' : '没有找到相关政策，请调整关键词或筛选条件'"><el-button v-if="otherDomainCount" @click="switchToOtherDomain">查看{{ domain === 'company' ? '国家政策' : '公司制度' }}中的 {{ otherDomainCount }} 项结果</el-button></el-empty>
        <template v-else-if="filtered.length">
          <div v-if="mode === 'graph'" class="kb-graph-wrap">
            <p class="kb-legend"><i></i> 政策板块 <i class="policy-dot"></i> 制度 / 条款 <span>连线表示主题归属，点击节点可继续检索</span></p>
            <div class="kb-graph-scroll">
              <svg class="kb-graph" viewBox="0 0 820 540" role="group" aria-label="政策知识图谱">
                <path v-for="(n, i) in graphNodes" :key="'edge'+i" :d="`M 410 270 Q 410 ${n.y} ${n.x} ${n.y}`" fill="none" stroke="#b9d9cf" stroke-width="1.8" />
                <g class="kb-root-node" transform="translate(410 270)"><circle r="69" fill="#176e59"/><text text-anchor="middle" fill="white"><tspan x="0" y="-3">{{ topic ? topicName : domain === 'public' ? '国家与地方政策' : domain === 'company' ? '公司制度与办理' : '参考资料' }}</tspan><tspan x="0" y="23" font-size="12">{{ filtered.length }} 项政策</tspan></text></g>
                <g v-for="n in graphNodes" :key="n.id" class="kb-graph-node" :class="{ 'is-policy': !!topic }" :transform="`translate(${n.x} ${n.y})`" role="button" tabindex="0" :aria-label="n.label" @click="activateNode(n.id)" @keydown.enter.prevent="activateNode(n.id)" @keydown.space.prevent="activateNode(n.id)">
                  <title>{{ n.label }}</title><rect x="-123" y="-38" width="246" height="76" rx="14"/><text text-anchor="middle"><tspan v-for="(line, i) in wrapLabel(n.label)" :key="i" x="0" :y="-10 + i * 19">{{ line }}</tspan><tspan x="0" y="29" class="kb-node-sub">{{ n.sub }}</tspan></text>
                </g>
              </svg>
            </div>
            <div class="kb-graph-footer"><span>{{ topic ? '可从当前主题继续查看相关政策' : '同一政策可关联多个板块' }}</span><template v-if="topic && filtered.length > graphSize"><el-button :disabled="graphPage === 0" @click="graphPage--">上一组</el-button><span>{{ graphPage + 1 }} / {{ Math.ceil(filtered.length / graphSize) }}</span><el-button :disabled="(graphPage + 1) * graphSize >= filtered.length" @click="graphPage++">下一组</el-button></template></div>
          </div>
          <div v-else class="kb-reading">
            <section v-for="t in visibleSections" :key="t.id" class="kb-section">
              <div class="kb-section-title"><h4>{{ t.name }}</h4><span>{{ count(t.id) }} 项政策</span><el-button v-if="!topic" text @click="selectTopic(t.id)">查看板块 →</el-button></div>
              <div class="kb-policy-grid"><button v-for="g in sectionGroups(t.id)" :key="g.id" class="kb-policy-card" @click="selectGroup(g)"><strong>{{ g.title }}</strong><span>{{ g.region }}{{ matchReason(g.id) ? ' · ' + matchReason(g.id) : '' }}</span><small>{{ g.source }}</small><em>阅读政策 →</em></button></div>
            </section>
            <el-button v-if="topic && filtered.length > cardLimit" class="kb-more" @click="cardLimit += 24">加载更多政策（剩余 {{ filtered.length - cardLimit }} 项）</el-button>
          </div>
        </template>
      </div>
    </div>
    <el-dialog v-model="detailOpen" :title="selected?.title" width="min(880px, 94vw)" class="kb-policy-dialog" destroy-on-close>
      <template v-if="selected">
        <div class="kb-detail-meta"><span>{{ selected.region }}</span><span>{{ selected.source }}</span></div>
        <div class="kb-related"><span>关联板块</span><el-button v-for="id in selected.topics" :key="id" size="small" @click="detailOpen = false; selectTopic(id)">{{ knowledgeTopics.find(t => t.id === id)?.name }}</el-button></div>
        <p v-if="selected.articles.length > 1" class="kb-reading-note">已按原文顺序汇集本政策的 {{ selected.articles.length }} 段内容。</p>
        <article v-for="a in selected.articles" :key="a.id" class="kb-provision">
          <div class="kb-provision-meta"><span>{{ a.source || '未标注出处' }}</span><span>生效：{{ a.effectiveFrom || '未标注' }}{{ a.effectiveTo ? ' · 有效至：' + a.effectiveTo : '' }}</span><span>{{ validityLabel(a) }} · {{ statusLabel(a.reviewStatus) }}</span></div>
          <p>{{ a.content || '此文档尚未提供可阅读正文。' }}</p>
          <a v-if="safeSource(a.sourceUrl)" :href="safeSource(a.sourceUrl)" target="_blank" rel="noopener noreferrer">查看来源原文 ↗</a>
          <div v-if="isHr" class="kb-maintain"><el-button size="small" @click="$emit('edit', a)">编辑内容</el-button><el-button size="small" @click="$emit('reindex', a)">重新索引</el-button><el-button size="small" type="danger" plain @click="$emit('remove', a)">删除内容</el-button></div>
        </article>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { KnowledgeArticle } from '../api/types'
import { groupPolicies, knowledgeTopics, policyDomain, policyValidity, policyValidityLabel, type PolicyGroup } from '../utils/knowledgeTopics'
import { searchKnowledge } from '../utils/knowledgeSearch'
const props = defineProps<{ articles: KnowledgeArticle[]; loading: boolean; isHr: boolean }>()
defineEmits<{ edit: [article: KnowledgeArticle]; reindex: [article: KnowledgeArticle]; remove: [article: KnowledgeArticle] }>()
const query = ref(''), region = ref(''), source = ref(''), validity = ref(''), topic = ref(''), mode = ref('graph')
const graphPage = ref(0), graphSize = 8, cardLimit = ref(24), detailOpen = ref(false), selectedId = ref('')
const domain = ref('public')
const includeBody = ref(false)
const domains = [{id:'public',title:'国家政策图谱',hint:'全国与地方 · 政策主题 · 适用地区'}, {id:'company',title:'公司制度图谱',hint:'业务板块 · 企业文件 · 内部办理'}]
const groups = computed(() => groupPolicies(props.articles.filter(a => policyDomain(a) === domain.value)))
const selected = computed(() => groups.value.find(g => g.id === selectedId.value))
const regions = computed(() => [...new Set(groups.value.map(g => g.region))].sort())
const sources = computed(() => [...new Set(groups.value.map(g => g.source))].sort())
const matches = computed(() => searchKnowledge(groups.value.filter(g => (!region.value || g.region === region.value) && (!source.value || g.source === source.value) && (!validity.value || policyValidity(g.articles[0]) === validity.value)), query.value, includeBody.value))
const matching = computed(() => matches.value.map(m => m.group))
const otherDomainCount = computed(() => query.value.trim() ? searchKnowledge(groupPolicies(props.articles.filter(a => policyDomain(a) !== domain.value)), query.value, includeBody.value).length : 0)
function matchReason(id: string) { return matches.value.find(m => m.group.id === id)?.reason || '' }
function switchToOtherDomain() { const text = query.value; domain.value = domain.value === 'company' ? 'public' : 'company'; reset(); query.value = text }
const filtered = computed(() => matching.value.filter(g => !topic.value || g.topics.includes(topic.value)))
const populatedTopics = computed(() => knowledgeTopics.filter(t => count(t.id)))
const visibleSections = computed(() => populatedTopics.value.filter(t => !topic.value || t.id === topic.value))
const topicName = computed(() => knowledgeTopics.find(t => t.id === topic.value)?.name || '全部政策板块')
function count(id: string) { return matching.value.filter(g => g.topics.includes(id)).length }
function sectionGroups(id: string) { return matching.value.filter(g => g.topics.includes(id)).slice(0, topic.value ? cardLimit.value : 4) }
function selectTopic(id: string) { topic.value = id; graphPage.value = 0; cardLimit.value = 24 }
function reset() { query.value = ''; region.value = ''; source.value = ''; validity.value = ''; selectTopic('') }
function validityLabel(article: KnowledgeArticle) { return policyValidityLabel(article) }
function selectGroup(group: PolicyGroup) { selectedId.value = group.id; detailOpen.value = true }
function activateNode(id: string) { if (!topic.value) selectTopic(id); else { const g = filtered.value.find(g => g.id === id); if(g) selectGroup(g) } }
function wrapLabel(label: string) { return label.length > 15 ? [label.slice(0,15), label.slice(15,29) + (label.length > 29 ? '…' : '')] : [label] }
function statusLabel(status: string) { return ({ APPROVED: '已审核', REFERENCE: '手册摘编 · 待核验', PENDING_REVIEW: '待审核', DRAFT: '草稿', REJECTED: '已驳回' } as Record<string,string>)[status] || status || '未标注' }
function safeSource(value?: string) { try { const url = new URL(value || ''); return ['https:', 'http:'].includes(url.protocol) ? url.href : undefined } catch { return undefined } }
const graphNodes = computed(() => {
  const nodes = topic.value ? filtered.value.slice(graphPage.value * graphSize, (graphPage.value + 1) * graphSize).map(g => ({ id: g.id, label: g.title, sub: `${g.region} · ${matchReason(g.id) || validityLabel(g.articles[0])}` })) : populatedTopics.value.map(t => ({ id: t.id, label: t.name, sub: `${count(t.id)} 项政策` }))
  const half = Math.ceil(nodes.length / 2)
  return nodes.map((n,i) => ({ ...n, x: i < half ? 145 : 675, y: 58 + (i % half) * (424 / Math.max(1, half - 1)) }))
})
watch([query, region, source, validity], () => { graphPage.value = 0; cardLimit.value = 24 })
watch(query, () => { selectTopic('') })
watch(includeBody, () => { graphPage.value = 0; cardLimit.value = 24 })
watch(groups, () => { if (!selected.value) detailOpen.value = false; graphPage.value = 0 })
</script>

<style scoped>
.kb-search-scope{display:flex;align-items:center;gap:14px;flex-wrap:wrap;margin-top:8px;color:#6d7e76;font-size:12px}
.kb-domains{display:flex;gap:12px;flex-wrap:wrap;margin-bottom:20px}.kb-domains button{flex:1;min-width:180px;display:grid;gap:9px;text-align:left;padding:18px;border:1px solid #d2dfdd;border-radius:14px;background:white;color:#34544a;cursor:pointer}.kb-domains button[aria-pressed=true]{background:#eaf4ef;border-color:#43866d;box-shadow:0 0 0 1px #43866d}.kb-domains span,.kb-domains small{font-size:12px;color:#6b8079}

.knowledge-explorer{--kb-green:#176e59;color:#233c34}.kb-search{padding:26px;background:linear-gradient(110deg,#eaf4ef,#f8faf6);border:1px solid #d8e7de;border-radius:16px;margin-bottom:22px}.kb-eyebrow{letter-spacing:2px;font-size:11px;font-weight:700;color:var(--kb-green)}.kb-search h2{font-size:25px;margin:10px 0}.kb-search p,.kb-view-heading p{color:#6d7e76;line-height:1.7;font-size:13px}.kb-search :deep(.el-input__wrapper){min-height:46px}.kb-filters{display:flex;align-items:center;gap:10px;margin-top:12px;flex-wrap:wrap}.kb-filters .el-select{width:190px}.kb-filters>span{font-size:12px;color:#65796e;margin-left:auto}.kb-layout{display:grid;grid-template-columns:220px minmax(0,1fr);gap:22px}.kb-topics{display:flex;flex-direction:column;gap:5px}.kb-topics button{text-align:left;padding:13px 14px;border:1px solid transparent;background:transparent;border-radius:10px;cursor:pointer;color:#355047;display:grid;gap:7px}.kb-topics button.active{background:#e9f4ee;border-color:#c3dfd0;color:var(--kb-green)}.kb-topics button:hover{background:#f0f6f3}.kb-topics strong{font-size:14px;display:flex;justify-content:space-between;gap:5px}.kb-topics small{font-weight:400}.kb-topics span{font-size:11px;color:#72827b}.kb-main{min-width:0}.kb-view-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:15px;flex-wrap:wrap}.kb-view-heading h3{margin:0;font-size:20px}.kb-view-heading p{margin:6px 0 0}.kb-switch{display:flex;border:1px solid #dbe5df;border-radius:9px;padding:3px;background:white}.kb-switch button{padding:8px 12px;border:0;border-radius:6px;background:white;color:#587068;cursor:pointer}.kb-switch button[aria-pressed=true]{background:var(--kb-green);color:white}.kb-graph-wrap{background:radial-gradient(#dce9e2 1px,transparent 1px);background-size:18px 18px;border:1px solid #deebe3;border-radius:14px;overflow:hidden}.kb-legend{display:flex;align-items:center;gap:9px;font-size:11px;padding:12px 16px;color:#688075;margin:0;flex-wrap:wrap;background:#ffffffa8}.kb-legend i{width:9px;height:9px;border-radius:50%;background:#176e59}.kb-legend .policy-dot{background:#dbb262}.kb-legend span{margin-left:auto}.kb-graph-scroll{overflow-x:auto}.kb-graph{width:100%;min-width:620px;display:block;min-height:350px}.kb-graph-node{cursor:pointer;outline:none}.kb-graph-node rect{fill:#f4faf6;stroke:#b6d9c5;stroke-width:1.5}.kb-graph-node.is-policy rect{fill:#fffdf7;stroke:#e1d8b9}.kb-graph-node:hover rect,.kb-graph-node:focus rect{stroke:#176e59;stroke-width:3;fill:#e7f3eb}.kb-graph-node text{fill:#234d3e;font-size:12px;pointer-events:none}.kb-graph-node .kb-node-sub{fill:#7b897e;font-size:10px}.kb-root-node text{font-size:14px;font-weight:600}.kb-graph-footer{padding:12px;display:flex;align-items:center;justify-content:flex-end;gap:10px;background:#ffffffb8;font-size:12px;color:#6b7e74}.kb-graph-footer>span:first-child{margin-right:auto}.kb-section{margin-bottom:24px}.kb-section-title{display:flex;align-items:center;gap:12px;margin-bottom:12px}.kb-section-title h4{font-size:16px;margin:0}.kb-section-title span{font-size:12px;color:#7b877e}.kb-section-title .el-button{margin-left:auto}.kb-policy-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.kb-policy-card{background:white;border:1px solid #dae6de;border-radius:12px;padding:18px;display:flex;flex-direction:column;gap:10px;text-align:left;cursor:pointer;min-width:0;color:#2b463b}.kb-policy-card:hover{border-color:#69a58b;box-shadow:0 6px 18px #214a3610}.kb-policy-card strong{font-size:14px;line-height:1.7}.kb-policy-card span{font-size:12px;color:#6a8073}.kb-policy-card small{font-size:11px;color:#829085;overflow-wrap:anywhere}.kb-policy-card em{font-size:12px;color:var(--kb-green);font-style:normal;margin-top:auto}.kb-more{width:100%}.kb-detail-meta{display:flex;gap:12px;flex-wrap:wrap;color:#738277;font-size:12px}.kb-related{display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin:20px 0;color:#748478;font-size:12px}.kb-reading-note{padding:12px;background:#f0f7f2;color:#4a6a57;font-size:12px}.kb-provision{border-top:1px solid #e4ece6;padding:18px 0}.kb-provision-meta{display:flex;flex-wrap:wrap;gap:10px;color:#748075;font-size:12px}.kb-provision>p{white-space:pre-wrap;line-height:1.95;font-size:14px;color:#34483b;overflow-wrap:anywhere}.kb-maintain{display:flex;gap:8px;margin-top:16px}.kb-provision>a{color:#176e59}.kb-policy-dialog{overflow-wrap:anywhere}@media(max-width:950px){.kb-layout{grid-template-columns:1fr}.kb-topics{flex-direction:row;overflow-x:auto;padding-bottom:6px}.kb-topics button{min-width:165px}.kb-search{padding:18px}.kb-filters .el-select{width:160px}}@media(max-width:600px){.kb-policy-grid{grid-template-columns:1fr}.kb-search h2{font-size:21px}.kb-filters>span{margin-left:0}.kb-graph-footer{flex-wrap:wrap}.kb-search :deep(.el-input__inner){font-size:12px}}
</style>

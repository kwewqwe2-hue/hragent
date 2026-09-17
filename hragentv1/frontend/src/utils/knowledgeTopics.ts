import type { KnowledgeArticle } from '../api/types'

export function policyValidity(a: KnowledgeArticle | undefined, today = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai' }).format(new Date())) {
  if (!a || a.reviewStatus !== 'APPROVED' || !a.effectiveFrom) return 'unverified'
  if (a.effectiveFrom > today) return 'future'
  if (a.effectiveTo && a.effectiveTo < today) return 'historical'
  return 'current'
}
export function policyValidityLabel(a: KnowledgeArticle | undefined) {
  return { current: '当前适用', future: '待生效', historical: '历史版本', unverified: '待核验' }[policyValidity(a)]
}

// An official URL is evidence of provenance; a nationwide scope alone is not.
export function policyDomain(article: KnowledgeArticle): 'public' | 'company' {
  // Domain is subject ownership; reference status is shown independently, never as current law.
  if (/^hrmanual\.pdf/.test(article.source || '')) return 'public'
  if (/演示|测试来源/.test(article.source + article.title)) return 'company'
  if (/企业原始制度|企业提供制度|公司制度|企业制度|企业福利确认/.test(article.category)) return 'company'
  try {
    const url = new URL(article.sourceUrl || article.source?.match(/https:\/\/[^\s；;）)]+/)?.[0] || '')
    if (url.protocol === 'https:' && /(^|\.)(gov\.cn|npc\.gov\.cn)$/.test(url.hostname)) return 'public'
  } catch { /* Missing provenance remains a reference. */ }
  if (/法定休假政策|政策法规|国家法律|国家法规|中国劳动法规|地方性法规/.test(article.category)) return 'public'
  if (/CHR-|员工手册|HR |公司|企业|制度中心|SaaS 知识库上传|Company Policy|Company Leave Policy|Employee Handbook/i.test(article.source + article.category)) return 'company'
  return 'company'
}

// Navigation relationships only: a shared topic never implies legal equivalence.
export const knowledgeTopics = [
  { id: 'leave', name: '休假与考勤', hint: '年假 · 工时 · 加班 · 生育假', pattern: /休假|年假|考勤|工时|加班|调休|产假|婚假|病假|请假|事假|丧假|假期|放假|工作时间|计划生育|育儿|生育假|作息|迟到|旷工|早退|leave|attendance/i },
  { id: 'pay', name: '薪酬与福利', hint: '工资 · 津贴 · 员工福利', pattern: /工资|薪资|薪酬|津贴|福利|奖金|补助|抚恤|欠薪|个人所得税|专项.*扣除|遗属|salary|payroll/i },
  { id: 'insurance', name: '社保与公积金', hint: '社会保险 · 工伤 · 医疗保障', pattern: /社保|社会保险|公积金|工伤|养老|医疗|生育保险|失业保险|保险费|企业年金|缴存比例|因工死亡|伤亡人员/i },
  { id: 'travel', name: '差旅与报销', hint: '出差 · 住宿 · 费用标准', pattern: /差旅|出差|报销|住宿|交通费|餐费|travel/i },
  { id: 'finance', name: '财务与审批', hint: '支付 · 借款 · 审批权限', pattern: /财务|支付|借款|付款|审批权限|采购|预算|企业所得税|税务/i },
  { id: 'employment', name: '入离职与劳动合同', hint: '招聘 · 转正 · 续签 · 离职', pattern: /入职|离职|合同|招聘|试用|转正|调岗|调配|解聘|辞职|解除|终止|退休|就业|实习|劳务|劳动关系|劳动法|用工|人事管理|人力资源市场|工作许可/i },
  { id: 'growth', name: '培训与职业发展', hint: '培训 · 技能 · 绩效 · 晋升', pattern: /培训|职业|技能|教育|绩效|晋升|人才|学徒|考核|述职/i },
  { id: 'care', name: '关怀与合规', hint: '员工支持 · 申诉 · 权益保障', pattern: /关怀|心理|压力|申诉|举报|合规|仲裁|争议|诉讼|权益|纪律|奖惩|奖罚|奖励类别|奖励情况|处罚|保密|竞业|安全|保护|工会|残疾|惩戒|监察|民法典|适用法律|代表大会|防暑|降温|EAP/i },
  { id: 'service', name: '证明与办事指南', hint: '材料 · 办理 · 联系方式', pattern: /证明|材料|办理|办事|联系方式|落户|档案|户籍|居住证|户口|入境|出境|自助服务/i },
  { id: 'general', name: '综合制度', hint: '企业文化 · 制度适用 · 其他规范', pattern: /./ }
]
export function policyTitle(article: KnowledgeArticle) {
  return article.title.replace(/^(?:手册|PDF)?第\s*\d+(?:[、,\-—]\d+)*\s*页\s*[｜|：:]\s*/, '').replace(/([\u4e00-\u9fff])\s+(?=[\u4e00-\u9fff])/g, '$1').trim().replace(/[。.]$/, '') || article.title
}
export function sourceFamily(article: KnowledgeArticle) {
  return (article.source || '未标注来源').replace(/\s*[·｜|]\s*(?:PDF)?第\s*\d+\s*[、\d\-—]*页.*$/, '').trim()
}
export function topicIds(article: KnowledgeArticle) {
  const assigned = knowledgeTopics.find(topic => topic.name === article.category)
  if (assigned) return [assigned.id]
  const title = policyTitle(article)
  const clause = title.includes('｜') ? title.split('｜').slice(1).join('｜') : title
  const classify = (text: string) => knowledgeTopics.slice(0, -1).filter(t => t.pattern.test(text)).map(t => t.id)
  let ids = classify(clause)
  if (!ids.length) ids = classify(title + ' ' + article.category)
  // Full-page references often contain unrelated terms; do not classify using entire bodies.
  return ids.length ? ids : ['general']
}
export interface PolicyGroup { id: string; title: string; source: string; region: string; topics: string[]; articles: KnowledgeArticle[] }
export function groupPolicies(articles: KnowledgeArticle[]): PolicyGroup[] {
  const groups = new Map<string, PolicyGroup>()
  for (const article of articles) {
    const title = policyTitle(article), source = sourceFamily(article)
    // Preserve source, region, dates and review state so different versions cannot silently merge.
    const ambiguousTitle = /^(手册资料|正文|目录|附录|未命名.*)$/.test(title)
    const id = JSON.stringify([title, source, article.region, article.effectiveFrom, article.effectiveTo, article.publishedAt, article.reviewStatus, ambiguousTitle ? article.id : null])
    const group = groups.get(id) || { id, title, source, region: article.region || '未标注范围', topics: [], articles: [] }
    group.articles.push(article)
    group.topics = [...new Set([...group.topics, ...topicIds(article)])]
    groups.set(id, group)
  }
  const pageNumber = (a: KnowledgeArticle) => Number(a.source?.match(/第\s*(\d+)/)?.[1] || 0)
  for (const group of groups.values()) group.articles.sort((a,b) => pageNumber(a) - pageNumber(b) || a.id - b.id)
  return [...groups.values()]
}

import type { KnowledgeArticle } from '../api/types'
import { knowledgeTopics, policyTitle, type PolicyGroup } from './knowledgeTopics'

// Search evidence is separate from navigation topics and policy applicability.
const normalize = (value: string) => (value || '').normalize('NFKC').toLowerCase()
  .replace(/社会保险/g, '社保').replace(/住房公积金/g, '公积金').replace(/年休假/g, '年假')
const compact = (value: string) => normalize(value).replace(/\s+/g, '')
export interface KnowledgeMatch { group: PolicyGroup; score: number; reason: string }

function fields(article: KnowledgeArticle, includeBody: boolean) {
  const title = policyTitle(article), parts = title.split(/[｜|]/)
  const values = [{ text: parts[parts.length - 1] || title, score: 120, reason: '标题匹配' }]
  if (parts.length > 1) values.push({ text: parts.slice(0, -1).join(' '), score: 95, reason: '政策名称匹配' })
  // Only explicit headings are subject evidence. Lists of payroll items or citations are not headings.
  for (const line of (article.content || '').split(/\r?\n/)) {
    const heading = line.trim().match(/^(?:#{1,4}\s+(.{2,60})|【([^】]{2,60})】\s*|第[一二三四五六七八九十百零\d]+[章节]\s+(.{2,60})|第[一二三四五六七八九十百零\d]+条\s*[［\[【]([^］\]】]{2,60})[］\]】])$/)
    if (heading) values.push({ text: heading.slice(1).find(Boolean) || '', score: 85, reason: '条款标题匹配' })
  }
  const topic = knowledgeTopics.find(t => t.name === article.category)
  if (topic) values.push({ text: topic.name, score: 60, reason: '板块匹配' })
  // Use actual document identifiers, not unrelated words in the source footer or file description.
  for (const code of (article.source || '').match(/CHR-[A-Z]+-\d+(?:\s+[A-Z]\d+)?/gi) || [])
    values.push({ text: code, score: 100, reason: '文件编号匹配' })
  if (includeBody) values.push({ text: article.content || '', score: 15, reason: '正文提及' })
  return values.map(f => ({ ...f, text: compact(f.text) }))
}

export function searchKnowledge(groups: PolicyGroup[], query: string, includeBody = false): KnowledgeMatch[] {
  const terms = normalize(query).trim().split(/[\s，,；;]+/).filter(Boolean)
  if (!terms.length) return groups.map(group => ({ group, score: 0, reason: '' }))
  return groups.map(group => {
    // Every term must be supported by one article; don't stitch evidence across unrelated fragments.
    let best: KnowledgeMatch | undefined
    for (const article of group.articles) {
      const evidence = fields(article, includeBody)
      let score = 0, weakest = 1000, reason = '', subjectMatched = false, valid = true
      for (const term of terms) {
        const hit = evidence.find(f => f.text.includes(term))
        if (!hit) {
          // Region qualifies an otherwise relevant topic, but is never enough by itself.
          if (compact(article.region).includes(term)) continue
          valid = false; break
        }
        subjectMatched = true; score += hit.score
        if (hit.score < weakest) { weakest = hit.score; reason = hit.reason }
      }
      if (valid && subjectMatched && (!best || score > best.score)) best = { group, score, reason }
    }
    return best
  }).filter((match): match is KnowledgeMatch => !!match).sort((a, b) => b.score - a.score)
}

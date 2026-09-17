import { readFile } from 'node:fs/promises'
const base = 'http://localhost:8080/api'
const data = JSON.parse(await readFile(new URL('../n8nwork/knowledge-files/hrmanual-index.json', import.meta.url), 'utf8'))
const login = await fetch(base + '/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: process.env.HRAGENT_IMPORT_USERNAME || 'wanghr', password: process.env.HRAGENT_IMPORT_PASSWORD || '123456' }) })
const session = (await login.json()).data
if (!session?.token || session.user.role !== 'HR') throw new Error('需要当前企业的 HR 账号导入知识库')
const headers = { 'Content-Type': 'application/json', Authorization: `Bearer ${session.token}`, 'X-Workspace-Id': String(session.user.tenantId) }
async function call(path, method = 'GET', body) {
  const response = await fetch(base + path, { method, headers, body: body ? JSON.stringify(body) : undefined })
  const payload = await response.json()
  if (!response.ok || !payload.success) throw new Error(`${method} ${path}: ${payload.message || response.status}`)
  return payload.data
}
const existing = await call('/admin/knowledge')
let inserted = 0, unchanged = 0
const retainedSources = new Set()
for (const [index, chunk] of data.chunks.entries()) {
  const source = `${data.fileName} · ${data.version} · PDF第${chunk.page}页 · 片段${index + 1}`
  retainedSources.add(source)
  const body = { category: '员工手册参考', title: `手册第${chunk.page}页｜${chunk.title}`.slice(0, 150),
    content: chunk.content, source, region: '参考资料（适用范围以原文为准）', reviewStatus: 'APPROVED',
    effectiveFrom: null, effectiveTo: null, publishedAt: null }
  const previous = existing.find(a => a.category === body.category && a.source === source)
  if (previous && previous.content === body.content) { unchanged++; continue }
  await call(previous ? `/admin/knowledge/${previous.id}` : '/admin/knowledge', previous ? 'PUT' : 'POST', body)
  inserted++
  if (inserted % 100 === 0) console.log(`Imported ${inserted} sections locally`)
}
for (const article of existing) {
  if (article.category === '员工手册参考' && article.source?.startsWith(`${data.fileName} · ${data.version} · `)
      && !retainedSources.has(article.source) && article.reviewStatus === 'APPROVED') {
    await call(`/admin/knowledge/${article.id}`, 'PUT', { ...article, reviewStatus: 'SUPERSEDED' })
  }
}
console.log(JSON.stringify({ file: data.fileName, version: data.version, pages: data.pages, sections: data.chunks.length, inserted, unchanged, tenantId: session.user.tenantId, sha256: data.sha256 }))

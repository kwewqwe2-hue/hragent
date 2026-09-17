const assert = require('node:assert/strict')
const fs = require('node:fs/promises')
const path = require('node:path')
const playwright = require(path.join(process.env.USERPROFILE, '.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright'))
;(async () => {
  const base = 'http://localhost:8080/api'
  const login = await fetch(base + '/auth/login', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({username:'zhangsan',password:'123456'})})
  const {data: session} = await login.json()
  for (const [question, expected] of [['年假有多少天', /5天|5 天/], ['试用期最长多久', /六个月|6个月/], ['加班工资怎么算', /150|百分之一百五十/], ['婚假几天', /婚假/], ['医疗期怎么计算', /医疗期/], ['未休年假如何补偿', /300|百分之三百/]]) {
    const res = await fetch(base + '/web-chat/messages', {method:'POST', headers:{'Content-Type':'application/json',Authorization:`Bearer ${session.token}`,'X-Workspace-Id':String(session.user.tenantId)},body:JSON.stringify({message:question,conversationId:'handbook-smoke'})})
    const payload = await res.json(); const answer = payload.data
    assert.equal(answer.provider, 'handbook-knowledge', JSON.stringify(payload))
    const content = answer.answer
    assert.match(content, expected, question)
    assert.match(content, /PDF第\d+页/); assert.match(content, /2022年版/)
    console.log(JSON.stringify({question,sources:content.match(/来源：[^\n]+/g)}))
  }
  const browser = await playwright.chromium.launch({headless:true,channel:'msedge'})
  try {
    const page = await browser.newPage({viewport:{width:1440,height:1000}})
    const errors=[]; page.on('pageerror',e=>errors.push(e.message))
    await page.goto('http://localhost:5174/?view=services')
    await page.getByRole('button',{name:'登录',exact:true}).click()
    await page.locator('.composer textarea').waitFor()
    assert.equal(await page.getByRole('button',{name:'员工服务 · 自助办理',exact:true}).count(),0)
    await page.locator('.composer textarea').fill('年假有多少天')
    await page.getByRole('button',{name:'发送',exact:true}).click()
    await page.locator('.message-row.assistant .markdown-content').filter({hasText:'PDF第'}).waitFor({timeout:30000})
    await fs.mkdir(path.resolve(__dirname,'../.artifacts'),{recursive:true})
    await page.screenshot({path:path.resolve(__dirname,'../.artifacts/handbook-chat.png'),fullPage:true})
    assert.deepEqual(errors,[])
    console.log('PASS: original chat layout, handbook answer, page citations, no browser errors')
  } finally {await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1})

// Browser contract test: real built Vue UI, isolated synthetic API responses, no business writes.
const assert = require('node:assert/strict');
const path = require('node:path');
const fs = require('node:fs/promises');
const http = require('node:http');
const { chromium } = require(path.join(process.env.USERPROFILE, '.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright'));
const dist = path.resolve('hragent-chat/dist');
const artifacts = path.resolve('.artifacts/agent-approvals');
const labels = { PENDING_MANAGER: '待主管审批', PENDING_HR: '待 HR 备案', APPROVED: '已通过', REJECTED: '已驳回' };
const makeRow = id => ({ id, employeeName: '测试员工', managerName: '测试主管', leaveType: 'ANNUAL', leaveTypeLabel: '年假', days: 1, reason: '审批弹窗测试', startDate: '2026-09-15', endDate: '2026-09-15', status: 'PENDING_MANAGER', statusLabel: labels.PENDING_MANAGER });
(async () => {
  await fs.mkdir(artifacts, { recursive: true });
  const server = http.createServer(async (req, res) => {
    try {
      let relative = decodeURIComponent(new URL(req.url, 'http://localhost').pathname).replace(/^\/agent\/?/, '');
      if (!relative || !path.extname(relative)) relative = 'index.html';
      const file = path.resolve(dist, relative);
      if (!file.startsWith(dist + path.sep)) { res.writeHead(403).end(); return; }
      const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.png': 'image/png', '.gif': 'image/gif' };
      res.setHeader('Content-Type', types[path.extname(file)] || 'application/octet-stream'); res.end(await fs.readFile(file));
    } catch { res.writeHead(404).end(); }
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const root = `http://127.0.0.1:${server.address().port}/agent/`;
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  let rows = [makeRow(101)], writes = [], failNext = false, queueError = false;
  const errors = [];
  async function actor(role, id, width = 1280) {
    const context = await browser.newContext({ viewport: { width, height: 900 } });
    const user = { id, username: 'fixture-' + role, name: '测试' + role, publicId: 'FIX-' + id, email: '', platformAdmin: false, tenantId: 1, role, employeeStatus: 'ACTIVE', workspaceName: '审批测试企业' };
    await context.addInitScript(u => {
      if (!localStorage.getItem('hragent_token')) {
        localStorage.setItem('hragent_token', 'fixture-token'); localStorage.setItem('hragent_user', JSON.stringify(u)); localStorage.setItem('hragent_workspace_id', '1');
      }
    }, user);
    await context.route('**/api/**', async route => {
      const req = route.request(), url = new URL(req.url()).pathname;
      const reply = (data, status = 200, message = '') => route.fulfill({ status, json: { success: status < 400, data, message } });
      if (url.endsWith('/auth/me')) return reply(user);
      if (url.endsWith('/leave/my')) return reply(role === 'EMPLOYEE' ? rows : []);
      if (url.endsWith('/leave/manager/pending') || url.endsWith('/leave/hr/pending')) {
        if (queueError) return reply(null, 503, '测试连接中断');
        return reply(rows.filter(r => r.status === (role === 'HR' ? 'PENDING_HR' : 'PENDING_MANAGER')));
      }
      if (req.method() === 'PUT') {
        writes.push({ url, body: req.postDataJSON() });
        if (failNext) { failNext = false; return reply(null, 500, '测试提交失败，请重试'); }
        // Deliberately keep the request in flight to test double-click protection.
        await new Promise(resolve => setTimeout(resolve, 150));
        const id = Number(url.match(/\/(\d+)\//)[1]), row = rows.find(r => r.id === id), body = req.postDataJSON();
        if (!row || row.status !== (role === 'HR' ? 'PENDING_HR' : 'PENDING_MANAGER')) return reply(null, 400, '申请已处理');
        row.status = body.approved ? (role === 'HR' ? 'APPROVED' : 'PENDING_HR') : 'REJECTED'; row.statusLabel = labels[row.status];
        row[role === 'HR' ? 'hrOpinion' : 'managerOpinion'] = body.opinion;
        row[role === 'HR' ? 'hrRecordedAt' : 'managerReviewedAt'] = '2026-09-10T12:00:00';
        return reply(row);
      }
      if (url.endsWith('/medical-record')) return route.fulfill({ contentType: 'application/pdf', body: '%PDF-1.4 fixture' });
      return reply([]);
    });
    const page = await context.newPage(); page.setDefaultTimeout(15000); page.on('pageerror', e => errors.push(e.message));
    await page.goto(root); await page.locator('.inbox-button').waitFor();
    return { page, context };
  }
  const refresh = page => page.evaluate(() => window.dispatchEvent(new Event('focus')));
  try {
    const employee = await actor('EMPLOYEE', 1), manager = await actor('MANAGER', 2), hr = await actor('HR', 3, 390);
    await manager.page.getByRole('button', { name: '立即审核', exact: true }).click();
    await manager.page.getByRole('button', { name: '通过申请', exact: true }).click();
    assert.equal(writes.length, 0, 'opening a confirmation must not submit');
    await manager.page.getByLabel('主管审批意见').fill('交接已安排，同意休假');
    await manager.page.screenshot({ path: path.join(artifacts, 'manager-confirm.png'), fullPage: true });
    await manager.page.getByRole('button', { name: '确认通过', exact: true }).click();
    await manager.page.getByText(/申请 #101 已处理/).waitFor(); assert.equal(writes.length, 1); assert.equal(rows[0].status, 'PENDING_HR');
    await refresh(employee.page); await employee.page.getByRole('button', { name: '查看结果', exact: true }).click();
    await employee.page.getByText('主管已通过，等待 HR 审核。', { exact: true }).waitFor();
    assert.equal(await employee.page.getByRole('button', { name: '通过申请', exact: true }).count(), 0);
    await employee.page.getByRole('button', { name: '关闭请假审批' }).click();
    await refresh(hr.page); await hr.page.getByRole('button', { name: '立即审核', exact: true }).click();
    await hr.page.getByRole('button', { name: '审核通过', exact: true }).click();
    await hr.page.getByLabel('HR 审核意见').fill('资料齐全，已完成备案');
    assert.equal(await hr.page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), false);
    assert.equal(await hr.page.locator('dialog').evaluate(d => d.scrollWidth > d.clientWidth + 1), false);
    await hr.page.screenshot({ path: path.join(artifacts, 'hr-mobile.png'), fullPage: true });
    await hr.page.getByRole('button', { name: '确认通过', exact: true }).click();
    await hr.page.getByText(/申请 #101 已处理/).waitFor(); assert.equal(writes.length, 2); assert.equal(rows[0].status, 'APPROVED');
    await refresh(employee.page); await employee.page.getByRole('button', { name: '查看结果', exact: true }).click();
    await employee.page.getByText('审核已完成，假期余额和日历已更新。', { exact: true }).waitFor();
    await employee.page.getByText('资料齐全，已完成备案', { exact: false }).waitFor();
    await employee.page.screenshot({ path: path.join(artifacts, 'employee-result.png'), fullPage: true });
    await employee.page.getByRole('button', { name: '关闭请假审批' }).click(); await employee.page.reload();
    await employee.page.waitForResponse(r => r.url().endsWith('/leave/my')).catch(() => {});
    assert.equal(await employee.page.locator('.approval-toast').count(), 0, 'read results should stay read after reload');

    rows.push(makeRow(102)); await manager.page.getByRole('button', { name: '刷新', exact: true }).click();
    const second = manager.page.locator('[data-leave-id="102"]'); await second.getByRole('button', { name: '驳回申请', exact: true }).click();
    assert.equal(await second.getByRole('button', { name: '确认驳回', exact: true }).isDisabled(), true);
    await second.getByLabel('主管审批意见').fill('日期需要调整，请重新提交'); failNext = true;
    await second.getByRole('button', { name: '确认驳回', exact: true }).click(); await manager.page.getByText('测试提交失败，请重试', { exact: true }).waitFor();
    assert.equal(rows[1].status, 'PENDING_MANAGER'); assert.equal(await second.getByLabel('主管审批意见').inputValue(), '日期需要调整，请重新提交');
    await second.getByRole('button', { name: '确认驳回', exact: true }).click(); await manager.page.getByText(/申请 #102 已处理/).waitFor();
    assert.equal(rows[1].status, 'REJECTED'); await refresh(employee.page);
    await employee.page.getByRole('button', { name: '查看结果', exact: true }).click(); await employee.page.getByText('日期需要调整，请重新提交', { exact: false }).waitFor();

    rows.push({ ...makeRow(103), status: 'PENDING_HR', statusLabel: labels.PENDING_HR, managerOpinion: '同意', managerReviewedAt: '2026-09-10T12:01:00', medicalRecordId: 5 });
    await hr.page.getByRole('button', { name: '刷新', exact: true }).click();
    const third = hr.page.locator('[data-leave-id="103"]');
    const download = hr.page.waitForEvent('download'); await third.getByRole('button', { name: '查看病假原始材料', exact: true }).click(); assert.ok((await download).suggestedFilename().endsWith('.pdf'));
    await third.getByRole('button', { name: '驳回申请', exact: true }).click(); await third.getByLabel('HR 审核意见').fill('请补齐证明材料');
    await third.getByRole('button', { name: '确认驳回', exact: true }).click(); await hr.page.getByText(/申请 #103 已处理/).waitFor(); assert.equal(rows[2].status, 'REJECTED');

    rows.push(makeRow(104)); queueError = true; await manager.page.getByRole('button', { name: '刷新', exact: true }).click(); await manager.page.getByText('测试连接中断', { exact: false }).waitFor();
    queueError = false; await manager.page.getByRole('button', { name: '重新同步', exact: true }).click();
    const fourth = manager.page.locator('[data-leave-id="104"]'); await fourth.getByRole('button', { name: '通过申请', exact: true }).click();
    rows[3].status = 'REJECTED'; rows[3].statusLabel = labels.REJECTED;
    await manager.page.getByRole('button', { name: '刷新', exact: true }).click(); await manager.page.getByText('这份申请已由其他审核人员处理，待办已更新。', { exact: true }).waitFor();
    assert.equal(await fourth.count(), 0);
    assert.deepEqual(errors, []);
    console.log('PASS: manager → HR → employee, both rejection paths, opinion validation, failure/retry, stale review, material download, read receipts, employee permissions, mobile layout; all business APIs intercepted.');
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
})().catch(error => { console.error(error); process.exitCode = 1; });

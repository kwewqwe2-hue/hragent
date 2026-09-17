// One-time repair for the exact synthetic case identified in the browser comment.
// Preserve its encrypted row in an archive; do not filter business records by keyword.
const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
(async () => {
  const base = 'http://localhost:8080/api';
  const login = await fetch(base + '/auth/login', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({username:'zhangsan',password:'123456'}) });
  const session = (await login.json()).data;
  assert.ok(session?.token); assert.equal(session.user.tenantId, 1);
  const headers = { Authorization:`Bearer ${session.token}`, 'X-Workspace-Id':'1' };
  async function mine() { const response=await fetch(base+'/employee-relations/cases',{headers});assert.ok(response.ok);return (await response.json()).data; }
  const before=await mine(), row=before.find(c=>c.id===2);
  if (!row) { console.log('Known test case is already absent.');return; }
  assert.equal(row.kind,'URGENT');assert.equal(row.status,'RESOLVED');assert.equal(row.anonymous,false);
  assert.match(row.facts,/^员工消息（待人工核实）：\s*【自动验收】我想伤害自己\s*2026-09-07T17:10:58\.549016844 · 补充\s*【自动验收】查询我的年假余额\s*$/);
  assert.match(row.reply,/【自动验收】合成场景验证结束，无真实危机/);
  assert.ok(Number.isSafeInteger(session.user.id));
  const sql=`CREATE TABLE IF NOT EXISTS er_test_case_archive_20260908 LIKE er_cases;
START TRANSACTION;
SELECT id FROM er_cases WHERE id=2 AND tenant_id=1 FOR UPDATE;
INSERT INTO er_test_case_archive_20260908 SELECT * FROM er_cases WHERE id=2 AND tenant_id=1 AND employee_id=${session.user.id} AND status='RESOLVED' AND kind='URGENT' AND created_at='2026-09-08 01:10:57.270578' AND version=2;
DELETE c FROM er_cases c INNER JOIN er_test_case_archive_20260908 a ON c.id=a.id AND c.tenant_id=a.tenant_id AND c.version=a.version AND c.encrypted_body=a.encrypted_body AND c.encrypted_reply=a.encrypted_reply WHERE c.id=2 AND c.tenant_id=1;
COMMIT;
SELECT COUNT(*) AS archived FROM er_test_case_archive_20260908 WHERE id=2 AND tenant_id=1;`;
  const result=spawnSync('docker',['exec','-i','hragent-mysql','sh','-c','exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"'],{input:sql,encoding:'utf8'});
  assert.equal(result.status,0,'Archive transaction failed');
  const after=await mine();assert.ok(!after.some(c=>c.id===2));
  assert.deepEqual(after,before.filter(c=>c.id!==2));
  console.log('Archived exactly tenant 1 case 2; API confirms all other employee cases unchanged.');
})().catch(e=>{console.error(e.message);process.exitCode=1;});


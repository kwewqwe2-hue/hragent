// Exercise the actual Vue component logic with mock API responses, without employee DB writes.
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const ts=require('../hragent-chat/node_modules/typescript'),vue=require('../hragent-chat/node_modules/vue');
const file=fs.readFileSync('hragent-chat/src/components/ApprovalInbox.vue','utf8');
assert.ok(!file.includes('aria-label="请假审核动态"'));assert.ok(file.includes('Kaka · 有新进度'));
let body=file.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1].replace(/^import .*$/gm,'').replaceAll('import.meta.env.BASE_URL','"/agent/"');
body+='\nglobalThis.subject={load,reset,dismissNotifications,unread,mine,opened,seen};';
let own=[],queue=[],mounted,unmounted,lastTimeout,pendingResolve;const memory=new Map();
const props=vue.reactive({session:{token:'fixture',workspaceId:1,user:{id:1,tenantId:1,role:'EMPLOYEE'}}});
const context={...vue,defineProps:()=>props,defineExpose:()=>{},onMounted:f=>mounted=f,onBeforeUnmount:f=>unmounted=f,
 serviceRequest:async(_s,path)=>pendingResolve?new Promise(r=>pendingResolve.resolve=r):path==='/leave/my'?structuredClone(own):structuredClone(queue),apiErrorMessage:()=> 'temporary error',
 localStorage:{getItem:k=>memory.get(k)||null,setItem:(k,v)=>memory.set(k,v)},document:{hidden:false,addEventListener(){},removeEventListener(){}},window:{addEventListener(){},removeEventListener(){}},
 setInterval:()=>1,clearInterval(){},setTimeout:f=>{lastTimeout=f;return 2},clearTimeout(){lastTimeout=undefined},console};
vm.createContext(context);vm.runInContext(ts.transpileModule(body,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.None}}).outputText,context);
const row=(id,status)=>({id,status,statusLabel:status,startDate:'2026-09-15',endDate:'2026-09-17'});
(async()=>{
 const s=context.subject;own=[row(1,'APPROVED')];await s.load();assert.equal(s.unread.value,0,'historical baseline stays quiet');
 own.push(row(2,'PENDING_MANAGER'));await s.load();assert.equal(s.unread.value,1,'new submission notifies');
 s.dismissNotifications();await s.load();assert.equal(s.unread.value,0,'dismissed unchanged status stays quiet');
 own[1]=row(2,'APPROVED');await s.load();assert.equal(s.unread.value,1,'approval transition notifies');assert.ok(lastTimeout);lastTimeout();assert.equal(s.unread.value,0,'bubble auto dismisses');
 props.session={token:'other',workspaceId:2,user:{id:2,tenantId:2,role:'EMPLOYEE'}};own=[row(9,'APPROVED')];await new Promise(r=>setImmediate(r));assert.equal(s.unread.value,0);assert.equal(s.mine.value[0].id,9,'identity reset isolates notifications');
 pendingResolve={};const inFlight=s.load();unmounted();pendingResolve.resolve([row(99,'REJECTED')]);await inFlight;assert.equal(s.mine.value[0].id,9,'unmounted response ignored');
 console.log('PASS: quiet baseline, new submission and approval bubbles, dismissal, auto-hide, identity reset and stale response isolation; no real requests');
})().catch(e=>{console.error(e);process.exitCode=1});

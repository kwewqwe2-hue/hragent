// Runs the shipped Vue/TypeScript logic against synthetic responses; never writes employee applications.
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const ts=require('../hragent-chat/node_modules/typescript'),vue=require('../hragent-chat/node_modules/vue');
const compile=s=>ts.transpileModule(s,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}}).outputText;
const moduleContext={exports:{}};vm.createContext(moduleContext);vm.runInContext(compile(fs.readFileSync('hragent-chat/src/approvals.ts','utf8')),moduleContext);const adapter=moduleContext.exports;
const tick=()=>new Promise(r=>setImmediate(r));
const session=(role='EMPLOYEE',id=1)=>({token:'synthetic',workspaceId:1,user:{id,employeeProfileId:id,tenantId:1,role,employeeStatus:'ACTIVE'}});
const leave=(id,status,employeeId=1)=>({id,status,statusLabel:status,employeeId,employeeName:'测试员工',leaveTypeLabel:'年假',days:3,startDate:'2026-09-15',endDate:'2026-09-17',reason:'不应存入通知回执的申请说明'});
function harness(account=session()){
 let source=fs.readFileSync('hragent-chat/src/components/ApprovalCenter.vue','utf8').match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1].replace(/^import .*$/gm,'').replaceAll('import.meta.env.BASE_URL','"/agent/"');
 source+='\nglobalThis.subject={load,reset,refreshAfterSubmission,openInbox,closeInbox,choose,submitReview,returnToChat,unread,pendingUnread,latestMine,mine,pending,opened,seen,dialog,flash,opinion,actionError,sourceFailed,selected};';
 const store=new Map(),data=new Map(),failed=new Set(),calls=[],events=[],props=vue.reactive({session:account});let mounted,unmounted,hold,mutationResult,mutationError,modalCount=0;
 const context={...vue,...adapter,exports:{},defineProps:()=>props,defineEmits:()=>((...v)=>events.push(v)),defineExpose:()=>{},onMounted:f=>mounted=f,onBeforeUnmount:f=>unmounted=f,
  serviceRequest:async(s,path,options={})=>{calls.push({path,options,employee:s.user.id});if(options.method){if(mutationError)throw Error(mutationError);return structuredClone(mutationResult)}if(hold&&path==='/leave/my')return new Promise(r=>hold.resolve=r);if(failed.has(path))throw Error('暂时无法同步');return structuredClone(data.get(path)||[])},
  apiErrorMessage:e=>e.message,localStorage:{getItem:k=>store.get(k)||null,setItem:(k,v)=>store.set(k,v)},document:{hidden:false,addEventListener(){},removeEventListener(){},getElementById:()=>({focus(){}})},window:{addEventListener(){},removeEventListener(){}},setInterval:()=>1,clearInterval(){},setTimeout:()=>2,clearTimeout(){},console};
 vm.createContext(context);vm.runInContext(compile(source),context);const s=context.subject;s.dialog.value={open:false,showModal(){this.open=true;modalCount++},close(){this.open=false}};
 return {s,data,failed,calls,events,store,props,unmount:()=>unmounted(),hold:()=>hold={},release:value=>hold.resolve(value),result:r=>mutationResult=r,error:e=>mutationError=e,modals:()=>modalCount};
}
(async()=>{
 assert.equal(adapter.isSubmissionReceipt({provider:'hrssc-lifecycle',answer:'提交好啦，服务单 #18。已进入审核队列。'}),true);assert.equal(adapter.isSubmissionReceipt({provider:'hrssc-lifecycle',answer:'已提交，服务单 #18。'}),true);assert.equal(adapter.isSubmissionReceipt({provider:'hrssc-lifecycle',answer:'这份申请已经提交，无需重复提交。'}),false);assert.equal(adapter.isSubmissionReceipt({provider:'hrssc-lifecycle',answer:'以上是待提交草稿。'}),false);
 const h=harness(),s=h.s;h.data.set('/leave/my',[leave(1,'APPROVED')]);await s.load();assert.equal(s.unread.value,0,'old employee records are quiet');
 h.data.set('/leave/my',[leave(1,'APPROVED'),leave(2,'PENDING_MANAGER')]);await s.load();assert.equal(s.unread.value,1);assert.match(adapter.resultCopy(s.latestMine.value),/等待主管/);
 s.refreshAfterSubmission();assert.match(s.flash.value,/已送审/);await tick();await tick();
 h.data.set('/leave/my',[leave(1,'APPROVED'),leave(2,'APPROVED')]);await s.load();assert.ok(s.latestMine.value.complete);assert.match(adapter.resultCopy(s.latestMine.value),/办理完成/);
 assert.ok(![...h.store.values()].join('').includes('申请说明'),'receipts never store private request fields');s.returnToChat();assert.ok(h.events.some(e=>e[0]==='return'));assert.equal(s.unread.value,0);
 // Shared ids must not collide; linked lifecycle mirrors must not generate duplicate notifications.
 const own=harness();await own.s.load();own.data.set('/employment-certificates/my',[{id:5,status:'GENERATED',documentReady:true,certificateTypeLabel:'在职证明'}]);own.data.set('/lifecycle/mine',[{id:5,status:'COMPLETED',label:'产假申请',fields:{}},{id:6,status:'COMPLETED',certificateRequestId:5},{id:7,status:'DRAFT'},{id:8,status:'SUBMITTED',leaveRequestId:9}]);await own.s.load();assert.equal(own.s.unread.value,2);assert.equal(own.s.mine.value.length,2);
 for(const status of ['APPROVED','GENERATION_FAILED']){const row=adapter.normalizeApproval('certificate',{id:5,status});assert.equal(row.complete,false);assert.doesNotMatch(adapter.resultCopy(row),/已经办理完成/)}
 assert.equal(adapter.normalizeApproval('lifecycle',{id:1,status:'APPROVED'}).complete,false);assert.match(adapter.resultCopy(adapter.normalizeApproval('lifecycle',{id:1,status:'NEEDS_INFO'})),/补充材料/);
 // Authorized pending queues auto-open a modal; own requests are excluded from HR review.
 const hr=harness(session('HR',42));hr.data.set('/lifecycle/hr/queue',[{id:20,employeeId:1,status:'SUBMITTED',label:'产假申请',fields:{}},{id:21,employeeId:42,status:'SUBMITTED',fields:{}}]);await hr.s.load();await tick();assert.equal(hr.s.pending.value.length,1);assert.equal(hr.modals(),1);assert.equal(hr.s.opened.value,true);
 const pending=hr.s.pending.value[0];hr.s.choose(pending,'approve');hr.s.opinion.value='核对无误，继续办理';hr.result({id:20,employeeId:1,status:'APPROVED',label:'产假申请',updatedAt:'new',fields:{}});hr.data.set('/lifecycle/hr/queue',[{id:20,employeeId:1,status:'APPROVED',label:'产假申请',updatedAt:'new',fields:{}}]);await hr.s.submitReview();await tick();assert.ok(hr.calls.some(c=>c.path==='/lifecycle/hr/20/review'&&c.options.body.action==='APPROVED'));assert.ok(hr.events.some(e=>e[0]==='return'));assert.equal(hr.s.opened.value,false);assert.doesNotMatch(hr.s.flash.value,/已经办理完成/);
 // Failure keeps the review open and does not emit a completion/return signal.
 await hr.s.openInbox('pending');hr.s.choose(hr.s.pending.value[0],'complete');hr.s.opinion.value='文件已交付';hr.error('请先上传文件');const count=hr.events.length;await hr.s.submitReview();assert.match(hr.s.actionError.value,/上传文件/);assert.equal(hr.events.length,count);assert.equal(hr.s.opened.value,true);
 // Partial failures keep other types functioning while disabling failed queues.
 await tick();hr.failed.add('/lifecycle/hr/queue');hr.data.set('/employment-certificates/hr/all',[{id:3,employeeId:1,status:'PENDING_HR'}]);await hr.s.load();await tick();assert.ok(hr.s.pending.value.some(r=>r.kind==='certificate'));assert.ok(hr.s.sourceFailed(hr.s.pending.value.find(r=>r.kind==='lifecycle')));
 for(const kind of ['leave','certificate','lifecycle','onboarding','access']){const status=kind==='lifecycle'?'SUBMITTED':kind==='leave'||kind==='access'?'PENDING_MANAGER':'PENDING_HR';const row=adapter.normalizeApproval(kind,{id:3,status});const op=adapter.reviewOperation(row,kind==='leave'||kind==='access'?'MANAGER':'HR','approve','核对无误');assert.ok(op.path.includes('/3/'));assert.throws(()=>adapter.reviewOperation(row,'EMPLOYEE','approve',''),/身份|状态/)}
 const left=adapter.approvalSources({...session(),user:{...session().user,employeeStatus:'LEFT'}});assert.equal(left.length,1);assert.equal(left[0].path,'/lifecycle/mine');const hire=adapter.approvalSources({...session('NEW_HIRE'),user:{...session('NEW_HIRE').user,employeeStatus:'PENDING'}});assert.ok(hire.some(s=>s.kind==='onboarding'));assert.ok(!hire.some(s=>s.kind==='leave'));
 assert.ok(!adapter.approvalSources(session('HR')).some(s=>s.path==='/onboarding/my'));
 const reload=harness();for(const [key,value] of h.store)reload.store.set(key,value);reload.s.reset();await tick();reload.data.set('/leave/my',[leave(1,'APPROVED'),leave(2,'APPROVED'),leave(30,'PENDING_MANAGER')]);await reload.s.load();assert.ok(reload.s.unread.value>0,'new submissions after a prior visit still notify');
 // Unmounted requests cannot leak old account results into the new UI.
 const stale=harness();await stale.s.load();stale.hold();const job=stale.s.load();stale.unmount();stale.release([leave(99,'REJECTED')]);await job;assert.equal(stale.s.mine.value.length,0);
 console.log('PASS: submission receipts, completion states, 5 review types, auto-open reviewer modal, duplicates and drafts excluded, successful return and failed-review preservation, partial failures, privacy, role filtering and stale responses; no real applications');
})().catch(e=>{console.error(e);process.exitCode=1});

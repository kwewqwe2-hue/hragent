<template>
 <Teleport to="body">
  <aside v-if="!opened && (flash || (unread && !dismissed))" class="approval-toast" role="status" aria-label="Kaka 审核与办理消息">
   <img :src="assetBase+'kaka-3D.png'" alt="Kaka"/><small>Kaka · 有新进度</small>
   <button class="dismiss" aria-label="收起办理提醒" @click="dismissNotifications"><X :size="17"/></button>
   <strong>{{ flash ? '处理结果已更新' : pendingUnread ? `有 ${pendingUnread} 份申请等你处理` : latestMine?.complete ? '已经办理完成啦' : '你的申请有新进度' }}</strong>
   <p>{{ flash || (pendingUnread ? '打开申请，核对内容和材料后就能在这里处理。' : latestMine ? resultCopy(latestMine) : '打开查看最新的审核回复。') }}</p>
   <button class="primary" @click="openInbox(pendingUnread?'pending':'mine')">{{ pendingUnread?'立即处理':'查看结果' }}</button>
   <button @click="returnToChat">返回对话</button>
  </aside>
  <dialog ref="dialog" class="approval-dialog" aria-labelledby="approval-title" @close="opened=false" @cancel="onCancel">
   <header><div><h2 id="approval-title">审核与办理消息</h2><p>请假、证明、入职和员工服务的进度都在这里</p></div><button :disabled="submitting" aria-label="关闭审核弹窗" @click="closeInbox"><X :size="22"/></button></header>
   <nav><button v-if="reviewer" :disabled="submitting" :class="{active:tab==='pending'}" @click="selectTab('pending')">待我处理（{{pending.length}}）</button><button :disabled="submitting" :class="{active:tab==='mine'}" @click="selectTab('mine')">我的办理进度</button><button :disabled="loading||submitting" @click="load">刷新</button><button :disabled="submitting" @click="returnToChat">返回对话</button></nav>
   <main>
    <p v-if="syncError" class="error" role="alert">{{syncError}} <button @click="load">重新同步</button></p>
    <p v-if="actionError" class="error" role="alert">{{actionError}}</p>
    <p v-if="!ready">正在同步申请…</p><p v-else-if="!visibleRows.length" class="empty">{{tab==='pending'?'当前没有待你处理的申请。':'你还没有提交申请。'}}</p>
    <article v-for="row in visibleRows" :key="row.key" class="request-card" :data-request-key="row.key">
     <div class="heading"><strong>{{tab==='pending'?row.employeeName+' · ':''}}{{row.label}}</strong><span>{{row.statusLabel}}</span></div>
     <p class="muted">{{row.summary}} <small>#{{row.id}}</small></p>
     <p v-if="tab==='mine'">{{resultCopy(row)}}</p><p v-if="row.opinion"><b>处理回复：</b>{{row.opinion}}</p>
     <details><summary>查看申请内容</summary><template v-for="(value,key) in row.fields" :key="key"><p v-if="value"><b>{{key}}：</b>{{value}}</p></template></details>
     <details v-if="tab==='pending'&&row.kind==='leave'&&(row.raw.aiSummary||row.raw.aiEvidence)"><summary>查看辅助信息与依据</summary><p>{{row.raw.aiSummary}}</p><p>{{row.raw.aiEvidence}}</p></details>
     <div class="actions">
      <button v-if="row.kind==='leave'&&row.raw.medicalRecordId" :disabled="downloadBusy||submitting" @click="download(row, 'medical')">查看病假原始材料</button>
      <button v-if="row.kind==='lifecycle'&&row.raw.materialsName" :disabled="downloadBusy||submitting" @click="download(row,'materials')">查看申请材料</button>
      <button v-if="tab==='mine'&&((row.kind==='certificate'&&row.raw.documentReady)||(row.kind==='lifecycle'&&row.complete&&row.raw.fileName))" :disabled="downloadBusy||submitting" @click="download(row,'result')">下载办理结果</button>
      <button v-if="tab==='mine'&&row.status==='NEEDS_INFO'" @click="openBusiness(row)">前往补充材料</button>
      <button v-if="tab==='pending'&&row.kind==='certificate'" :disabled="submitting" @click="openBusiness(row)">核对档案与证明模板</button>
      <label v-if="tab==='pending'&&row.kind==='lifecycle'&&row.status==='APPROVED'" class="upload">{{row.raw.fileName?'替换交付文件':'上传交付文件'}}<input type="file" accept="application/pdf,.pdf" :disabled="submitting" @change="uploadResult(row,$event)"/></label>
     </div>
     <div v-if="tab==='pending'&&selected?.key!==row.key" class="actions"><button v-for="item in decisions(row,role)" :key="item.value" :class="item.value==='reject'?'reject':'primary'" :disabled="submitting||sourceFailed(row)" @click="choose(row,item.value)">{{item.label}}</button></div>
     <form v-if="selected?.key===row.key" class="review-form" @submit.prevent="submitReview">
      <strong>{{decisions(row,role).find(d=>d.value===decision)?.label}} · {{row.label}}</strong>
      <p>{{decision==='complete'?'请确认实际办理和所需文件交付均已完成，再标记办结。':decision==='needs-info'?'请写清还缺少什么，员工会收到补充提醒。':'核对内容后再确认，处理结果会同步给员工。'}}</p>
      <label :for="'opinion-'+row.key">处理意见</label><textarea :id="'opinion-'+row.key" v-model="opinion" rows="3" maxlength="600" required :disabled="submitting"/>
      <div class="actions"><button class="primary" :disabled="submitting||!opinion.trim()||sourceFailed(row)">{{submitting?'正在处理…':'确认处理'}}</button><button type="button" :disabled="submitting" @click="selected=null">取消</button></div>
     </form>
    </article>
   </main>
  </dialog>
 </Teleport>
</template>
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { X } from 'lucide-vue-next'
import { apiErrorMessage, serviceRequest } from '../api'
import { approvalSources, visibleApprovals, normalizeApproval, decisions, reviewOperation, resultCopy } from '../approvals'
import type { ApprovalRow, Decision } from '../approvals'
import type { AuthSession } from '../types'
const props=defineProps<{session:AuthSession}>()
const emit=defineEmits<{return:[];open:[title:string,path:string];requests:[]}>()
const assetBase=import.meta.env.BASE_URL
type Tab='pending'|'mine'
const role=computed(()=>props.session.user.role||''),reviewer=computed(()=>['HR','MANAGER'].includes(role.value))
const mine=ref<ApprovalRow[]>([]),pending=ref<ApprovalRow[]>([]),ready=ref(false),loading=ref(false),opened=ref(false),dialog=ref<HTMLDialogElement>(),tab=ref<Tab>('mine'),dismissed=ref(false)
const seen=ref<Record<string,string>>({}),failed=ref<string[]>([]),syncError=ref(''),actionError=ref(''),flash=ref(''),selected=ref<ApprovalRow|null>(null),decision=ref<Decision>('approve'),opinion=ref(''),submitting=ref(false),downloadBusy=ref(false)
const scope=computed(()=>`${props.session.workspaceId??props.session.user.tenantId}:${props.session.user.employeeProfileId??props.session.user.id}:${role.value}`)
const storageKey=computed(()=>`hragent_approval_center_v2:${scope.value}`)
const pendingChanges=computed(()=>pending.value.filter(r=>seen.value[`pending:${r.key}`]!==r.signature)),mineChanges=computed(()=>mine.value.filter(r=>seen.value[`mine:${r.key}`]!==r.signature))
const pendingUnread=computed(()=>pendingChanges.value.length),unread=computed(()=>pendingUnread.value+mineChanges.value.length),latestMine=computed(()=>mineChanges.value.find(r=>r.complete)||mineChanges.value[0])
const visibleRows=computed(()=>tab.value==='pending'?pending.value:mine.value)
let alive=true,epoch=0,version=0,timer:ReturnType<typeof setInterval>|undefined,toastTimer:ReturnType<typeof setTimeout>|undefined
let initialized=new Set<string>(),cache=new Map<string,ApprovalRow[]>(),observed='',refreshAgain=false,expectSubmission=false
function restoreSeen(){try{const s=JSON.parse(localStorage.getItem(storageKey.value)||'{}');seen.value=s&&typeof s==='object'&&!Array.isArray(s)?s:{}}catch{seen.value={}}}
function persist(){try{localStorage.setItem(storageKey.value,JSON.stringify(seen.value))}catch{/* receipts stay in memory */}}
function markRead(){if(!ready.value)return;for(const r of visibleRows.value)seen.value[`${tab.value}:${r.key}`]=r.signature;persist()}
function dismissNotifications(){dismissed.value=true;flash.value='';clearTimeout(toastTimer);for(const r of mine.value)seen.value[`mine:${r.key}`]=r.signature;for(const r of pending.value)seen.value[`pending:${r.key}`]=r.signature;persist()}
function sourceFailed(row:ApprovalRow){return failed.value.includes(`${tab.value}:${row.kind}`)}
async function load(){
 if(!alive||document.hidden||submitting.value)return
 if(loading.value){refreshAgain=true;return}
 const session=props.session,currentEpoch=epoch,currentVersion=++version,sources=approvalSources(session)
 loading.value=true
 try{
  const responses=await Promise.allSettled(sources.map(s=>serviceRequest(session,s.path)))
  if(!alive||epoch!==currentEpoch||version!==currentVersion)return
  const errors:string[]=[]
  responses.forEach((response,index)=>{
   const source=sources[index]
   if(response.status==='rejected'||!Array.isArray(response.value)){errors.push(source.key);return}
   const rows=visibleApprovals(source,response.value,session)
   if(!initialized.has(source.key)){
    if(source.side==='mine'&&!seen.value[`@baseline:${source.key}`]){for(const row of rows)seen.value[`mine:${row.key}`]=row.signature;seen.value[`@baseline:${source.key}`]='1'}
    initialized.add(source.key)
   }
   cache.set(source.key,rows)
  })
  failed.value=errors;syncError.value=errors.length?'部分业务暂时未同步成功，已保留其记录，请稍后刷新。':''
  mine.value=sources.filter(s=>s.side==='mine').flatMap(s=>cache.get(s.key)||[])
  pending.value=sources.filter(s=>s.side==='pending').flatMap(s=>cache.get(s.key)||[])
  ready.value=initialized.size>0;persist()
  const changes=JSON.stringify([...pendingChanges.value.map(r=>['pending',r.key,r.signature]),...mineChanges.value.map(r=>['mine',r.key,r.signature])])
  const newPending=pendingChanges.value.some(r=>!errors.includes(`pending:${r.kind}`))
  if(changes!==observed&&unread.value){dismissed.value=false;clearTimeout(toastTimer);toastTimer=setTimeout(dismissNotifications,18000)}
  const changed=changes!==observed;observed=changes
  if(selected.value&&!pending.value.some(r=>r.key===selected.value?.key&&r.signature===selected.value?.signature)){selected.value=null;actionError.value='申请状态已更新，请重新核对后处理。'}
  if(opened.value)markRead()
  else if(changed&&newPending&&reviewer.value)void openInbox('pending')
  if(expectSubmission){expectSubmission=false;if(unread.value)flash.value=''}
 }finally{if(alive&&currentEpoch===epoch&&currentVersion===version){loading.value=false;if(refreshAgain){refreshAgain=false;void load()}}}
}
async function openInbox(target?:Tab){clearTimeout(toastTimer);flash.value='';tab.value=reviewer.value?(target||'pending'):'mine';opened.value=true;dismissed.value=true;actionError.value='';await nextTick();if(!alive)return;if(!dialog.value?.open)dialog.value?.showModal();markRead();void load()}
function closeInbox(){if(submitting.value)return;selected.value=null;opened.value=false;dialog.value?.close()}
function returnToChat(){if(submitting.value)return;closeInbox();dismissNotifications();emit('return')}
function onCancel(e:Event){if(submitting.value)e.preventDefault();else selected.value=null}
function selectTab(value:Tab){tab.value=value;selected.value=null;actionError.value='';markRead()}
function choose(row:ApprovalRow,value:Decision){selected.value=row;decision.value=value;opinion.value='';actionError.value='';void nextTick(()=>document.getElementById('opinion-'+row.key)?.focus())}
async function submitReview(){
 if(!selected.value||!opinion.value.trim()||submitting.value||sourceFailed(selected.value))return
 const row=selected.value,currentEpoch=epoch,session=props.session
 submitting.value=true;actionError.value='';version++;loading.value=false
 try{
  const operation=reviewOperation(row,role.value,decision.value,opinion.value.trim())
  const response=await serviceRequest(session,operation.path,{method:operation.method,body:operation.body})
  if(!alive||epoch!==currentEpoch)return
  const result=normalizeApproval(row.kind,response)
  seen.value[`pending:${row.key}`]=result.signature;persist()
  pending.value=pending.value.filter(r=>r.key!==row.key);selected.value=null;opened.value=false;dialog.value?.close()
  flash.value='处理已保存。'+resultCopy(result);dismissed.value=false
  emit('return');clearTimeout(toastTimer);toastTimer=setTimeout(()=>{flash.value=''},18000)
 }catch(e){if(alive&&epoch===currentEpoch)actionError.value=apiErrorMessage(e)}
 finally{if(alive&&epoch===currentEpoch){submitting.value=false;void load()}}
}
function openBusiness(row:ApprovalRow){closeInbox();if(row.kind==='lifecycle'&&tab.value==='mine')emit('requests');else emit('open',row.label,row.kind==='certificate'?'/certificates':row.kind==='onboarding'?'/onboarding':'/hrssc')}
async function download(row:ApprovalRow,kind:'medical'|'materials'|'result'){
 if(downloadBusy.value||submitting.value)return
 const session=props.session,currentEpoch=epoch;downloadBusy.value=true;actionError.value=''
 try{
  const path=kind==='medical'?`/leave/${row.id}/medical-record`:kind==='materials'?`/lifecycle/${tab.value==='pending'?'hr/':''}${row.id}/materials`:row.kind==='certificate'?`/employment-certificates/${row.id}/download`:`/lifecycle/${row.id}/file`
  const blob=await serviceRequest(session,path,{binary:true}) as Blob;if(!alive||epoch!==currentEpoch)return
  const url=URL.createObjectURL(blob),link=document.createElement('a');link.href=url;link.download=`${row.label}-${row.id}.${blob.type.includes('pdf')?'pdf':blob.type.includes('png')?'png':'jpg'}`;link.click();setTimeout(()=>URL.revokeObjectURL(url),1000)
 }catch(e){if(alive&&epoch===currentEpoch)actionError.value=apiErrorMessage(e)}finally{if(alive&&epoch===currentEpoch)downloadBusy.value=false}
}
async function uploadResult(row:ApprovalRow,event:Event){
 const file=(event.target as HTMLInputElement).files?.[0];if(!file||submitting.value)return
 if(file.size>5*1024*1024||!file.name.toLowerCase().endsWith('.pdf')){actionError.value='请上传不超过 5 MB 的 PDF 文件。';return}
 const currentEpoch=epoch;submitting.value=true;actionError.value=''
 try{const body=new FormData();body.append('file',file);await serviceRequest(props.session,`/lifecycle/hr/${row.id}/file`,{method:'POST',body});if(!alive||epoch!==currentEpoch)return;row.raw.fileName=file.name;flash.value='交付文件已上传，确认实际办理完成后，再点击“确认办结”。'}catch(e){if(alive&&epoch===currentEpoch)actionError.value=apiErrorMessage(e)}finally{if(alive&&epoch===currentEpoch){submitting.value=false;void load()}}
}
function refreshAfterSubmission(){expectSubmission=true;flash.value='申请已送审啦，正在同步主管或经办人的审核进度。';dismissed.value=false;clearTimeout(toastTimer);toastTimer=setTimeout(()=>{flash.value=''},18000);void load()}
function reset(){epoch++;version++;clearTimeout(toastTimer);dialog.value?.close();opened.value=false;loading.value=false;submitting.value=false;mine.value=[];pending.value=[];ready.value=false;selected.value=null;downloadBusy.value=false;syncError.value='';actionError.value='';flash.value='';dismissed.value=false;cache=new Map();initialized=new Set();observed='';refreshAgain=false;expectSubmission=false;restoreSeen();void load()}
watch(()=>[scope.value,props.session.token],reset)
onMounted(()=>{restoreSeen();void load();timer=setInterval(load,5000);document.addEventListener('visibilitychange',load);window.addEventListener('focus',load)})
onBeforeUnmount(()=>{alive=false;epoch++;dialog.value?.close();clearInterval(timer);clearTimeout(toastTimer);document.removeEventListener('visibilitychange',load);window.removeEventListener('focus',load)})
defineExpose({openInbox,refreshAfterSubmission,load})
</script>
<style scoped>
.approval-toast{position:fixed;right:24px;bottom:26px;z-index:90;width:min(380px,calc(100vw - 32px));box-sizing:border-box;background:white;border:1px solid #d7e7df;box-shadow:0 12px 40px #14362a26;border-radius:18px;padding:22px;color:#213f34}.approval-toast img{width:62px;height:76px;object-fit:contain;float:left;margin:0 12px 4px -6px}.approval-toast small{display:block;color:#548477;margin:0 0 8px}.approval-toast strong{display:block;padding-right:18px}.approval-toast p{line-height:1.65;font-size:14px}.approval-toast .dismiss{position:absolute;right:8px;top:8px;border:0;padding:4px}.approval-toast button{margin:4px}.approval-dialog{box-sizing:border-box;width:min(760px,calc(100vw - 24px));max-height:86dvh;border:1px solid #dce7e2;border-radius:20px;padding:0;color:#243c32;background:#fafcfb;box-shadow:0 20px 80px #15392a40}.approval-dialog::backdrop{background:#132d245c}.approval-dialog header{display:flex;justify-content:space-between;gap:12px;align-items:center;padding:20px 24px;background:white}.approval-dialog h2{margin:0;font-size:20px}.approval-dialog header p{margin:7px 0 0;color:#6c7f75;font-size:13px}.approval-dialog nav{display:flex;gap:8px;padding:12px 20px;flex-wrap:wrap;border-block:1px solid #e3ece8}.approval-dialog nav .active{background:#e3f1e9;border-color:#9fc8b3}.approval-dialog main{padding:0 20px 20px}.request-card{background:white;border:1px solid #e0e8e3;border-radius:14px;margin-top:14px;padding:18px;font-size:14px;overflow-wrap:anywhere}.heading{display:flex;gap:12px;justify-content:space-between}.heading span{font-size:12px;background:#eef4ef;padding:4px 8px;border-radius:8px;white-space:nowrap}.request-card p{line-height:1.65;white-space:pre-wrap}.muted,.empty{color:#6a8175}.request-card summary{cursor:pointer;color:#4c7060}.actions{display:flex;gap:10px;margin-top:14px;flex-wrap:wrap}button,.upload{font:inherit;cursor:pointer;border:1px solid #dce7e2;border-radius:9px;background:white;color:#24534b;padding:8px 12px}.primary{background:#21684e;color:white;border-color:#21684e}.reject{background:#fff5f3;color:#a63d36;border-color:#efd1cd}button:disabled{opacity:.5;cursor:not-allowed}.review-form{margin-top:16px;border-top:1px solid #dfe9e3;padding-top:14px}.review-form label{display:block;margin:12px 0 6px}.review-form textarea{box-sizing:border-box;width:100%;font:inherit;border:1px solid #b6cec0;border-radius:9px;padding:10px;resize:vertical}.error{color:#a22f29;background:#fff1ef;padding:12px;border-radius:9px}.empty{padding:32px 10px;text-align:center}.upload input{display:block;max-width:210px;margin-top:8px}@media(max-width:600px){.approval-toast{right:16px;bottom:16px}.approval-dialog header{padding:16px}.approval-dialog nav{padding:10px}.approval-dialog main{padding:0 10px 12px}.request-card{padding:13px}.heading{align-items:flex-start}.heading span{white-space:normal}.approval-dialog{max-height:90dvh}}
</style>

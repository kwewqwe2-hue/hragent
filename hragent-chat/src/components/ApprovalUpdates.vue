<template>
 <aside class="approval-updates" aria-label="请假审核动态">
  <button @click="expanded=!expanded">{{ unread ? `请假审核有 ${unread} 条更新` : '请假审核动态' }} <span>{{ error ? '同步暂停' : '每 5 秒同步' }}</span></button>
  <section v-if="expanded">
   <div class="heading"><strong>我的请假进度</strong><button @click="load">刷新</button></div>
   <p v-if="error" role="status">暂时没能同步审核结果，连接恢复后会自动重试。</p>
   <p v-else-if="!ready">正在查询…</p><p v-else-if="!rows.length">你还没有提交请假申请。</p>
   <article v-for="row in rows.slice(0,5)" :key="row.id">
    <strong>#{{ row.id }} · {{ row.statusLabel }}</strong><p>{{ row.startDate }} 至 {{ row.endDate }}</p>
    <p v-if="row.hrOpinion">HR：{{ row.hrOpinion }}</p><p v-else-if="row.managerOpinion">主管：{{ row.managerOpinion }}</p>
   </article>
   <button @click="emit('open','我的请假','/my-leave')">查看完整记录</button>
  </section>
  <p v-if="notice" class="notice" role="status">{{ notice }} <button @click="expanded=true;unread=0;notice=''">查看结果</button></p>
 </aside>
</template>
<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { serviceRequest } from '../api'
import type { AuthSession } from '../types'
const props=defineProps<{session:AuthSession}>(),emit=defineEmits<{open:[title:string,path:string]}>()
type Row={id:number;status:string;statusLabel:string;startDate:string;endDate:string;managerOpinion?:string;hrOpinion?:string;managerReviewedAt?:string;hrRecordedAt?:string}
const rows=ref<Row[]>([]),expanded=ref(false),unread=ref(0),notice=ref(''),error=ref(false),ready=ref(false)
let alive=true,busy=false,generation=0,previous=new Map<number,string>(),timer:ReturnType<typeof setInterval>
const signature=(r:Row)=>JSON.stringify([r.status,r.managerReviewedAt,r.hrRecordedAt,r.managerOpinion,r.hrOpinion])
async function load(){
 if(busy||!alive||document.hidden)return
 const current=props.session,epoch=generation;busy=true
 try{
  const data=await serviceRequest(current,'/leave/my') as Row[]
  if(!alive||epoch!==generation)return
  if(ready.value){const changes=data.filter(r=>previous.has(r.id)?previous.get(r.id)!==signature(r):r.status==='APPROVED'||r.status==='REJECTED');if(changes.length){unread.value+=changes.length;notice.value=`你的请假申请 #${changes[0].id} 已更新：${changes[0].statusLabel}。`}}
  previous=new Map(data.map(r=>[r.id,signature(r)]));rows.value=data;ready.value=true;error.value=false
 }catch{if(alive&&epoch===generation)error.value=true}finally{busy=false}
}
function reset(){generation++;rows.value=[];previous.clear();ready.value=false;unread.value=0;notice.value='';error.value=false;expanded.value=false;void load()}
watch(()=>[props.session.token,props.session.workspaceId,props.session.user.id],reset)
watch(expanded,v=>{if(v){unread.value=0;notice.value='';void load()}})
onMounted(()=>{void load();timer=setInterval(load,5000);document.addEventListener('visibilitychange',load)})
onBeforeUnmount(()=>{alive=false;generation++;clearInterval(timer);document.removeEventListener('visibilitychange',load)})
</script>
<style scoped>
.approval-updates{position:relative;flex:0 0 auto;padding:8px 24px;border-bottom:1px solid #e3ece8;background:#f7fbf9;color:#24534b;font-size:14px}.approval-updates button{font:inherit;color:inherit;background:none;border:0;cursor:pointer;padding:6px}.approval-updates span{font-size:12px;color:#748b84;margin-left:10px}section{position:absolute;right:24px;top:44px;z-index:20;width:min(400px,calc(100vw - 48px));max-height:55vh;overflow:auto;background:white;box-shadow:0 12px 40px #203c3026;border:1px solid #dce8e3;border-radius:14px;padding:18px}.heading{display:flex;justify-content:space-between}article{padding:12px 0;border-bottom:1px solid #edf2f0}p{margin:6px 0;line-height:1.6;overflow-wrap:anywhere}.notice{color:#176e60}
</style>

<template>
 <section class="medical-box">
  <strong>病历或诊断证明</strong>
  <p>上传清晰的 PDF、JPG 或 PNG（不超过 5 MB）。保留姓名、医疗机构、日期和相关医嘱，可遮盖无关信息。</p>
  <input aria-label="病假材料" type="file" accept=".pdf,.jpg,.jpeg,.png" :disabled="busy || disabled || !startDate || !endDate" @change="upload" />
  <p v-if="!startDate || !endDate">先选好请假日期，再上传材料。</p>
  <p v-if="busy" role="status">正在识别材料，请稍候…</p><p v-if="result" role="status">{{ result }}</p>
  <p>初检通过后仍需主管和 HR 审核，不代表已获批。</p>
 </section>
</template>
<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import { http } from '../api/http'
const props=defineProps<{startDate:string;endDate:string;disabled:boolean}>(),emit=defineEmits<{ready:[id:number|null];busy:[value:boolean]}>()
const busy=ref(false),result=ref('');let revision=0,alive=true
watch(()=>[props.startDate,props.endDate],()=>{revision++;result.value='';emit('ready',null)})
onBeforeUnmount(()=>{alive=false})
async function upload(event:Event){
 const input=event.target as HTMLInputElement,file=input.files?.[0];input.value='';if(!file||busy.value)return
 if(file.size>5*1024*1024){result.value='文件不能超过 5 MB';return}
 emit('ready',null);busy.value=true;emit('busy',true);result.value='';const epoch=revision
 try{const form=new FormData();form.append('file',file);form.append('startDate',props.startDate);form.append('endDate',props.endDate);const {data}=await http.post('/leave/medical-records',form,{timeout:100000});if(alive&&epoch===revision){result.value=data.data.summary;emit('ready',data.data.id)}}
 catch{if(alive)result.value='这次材料没有完成初检，请按提示重新上传；尚未提交审批。'}finally{busy.value=false;emit('busy',false)}
}
</script>
<style scoped>.medical-box{background:#f5faf7;border:1px solid #cce0d7;padding:14px;border-radius:10px;margin-bottom:16px;color:#285648}.medical-box p{font-size:13px;line-height:1.6}.medical-box input{max-width:100%}</style>

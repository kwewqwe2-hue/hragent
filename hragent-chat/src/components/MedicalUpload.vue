<template>
 <div class="medical-upload">
  <label><span>{{ busy ? '正在识别病历，请稍候…' : label }}</span><input aria-label="上传病历并初检" type="file" accept=".pdf,.jpg,.jpeg,.png" :disabled="disabled || busy" @change="upload" /></label>
  <p v-if="error" role="alert">{{ error }}</p>
 </div>
</template>
<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { apiErrorMessage, serviceRequest } from '../api'
import type { AuthSession, ChatAction } from '../types'
const props = defineProps<{session: AuthSession; id: string; label: string; disabled: boolean}>()
const emit = defineEmits<{result:[response:{answer:string;actions?:ChatAction[];details?:string}];busy:[value:boolean]}>()
const busy=ref(false),error=ref(''); let alive=true
onBeforeUnmount(()=>{alive=false})
async function upload(event:Event){
 const input=event.target as HTMLInputElement,file=input.files?.[0]; input.value=''
 if(!file||busy.value||props.disabled)return
 if(file.size>5*1024*1024){error.value='请上传不超过 5 MB 的病历';return}
 const current=props.session, id=props.id
 busy.value=true;error.value='';emit('busy',true)
 try{const form=new FormData();form.append('file',file);const response=await serviceRequest(current,`/lifecycle/${id}/medical-record`,{method:'POST',body:form});if(alive&&current===props.session)emit('result',response)}
 catch(e){if(alive)error.value=apiErrorMessage(e)}finally{busy.value=false;emit('busy',false)}
}
</script>
<style scoped>
.medical-upload{max-width:100%}label{display:flex;align-items:center;flex-wrap:wrap;gap:10px;border:1px solid #bcd8d3;border-radius:12px;padding:14px;color:#126c62;background:#f3faf7;cursor:pointer}input{max-width:240px}p{color:#a44438;font-size:14px;line-height:1.6}
</style>

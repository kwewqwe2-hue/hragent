<template><div class="medical-review"><p>病假材料已完成文字识别与初检，真实性及医嘱仍需人工核验。</p><el-button :loading="busy" @click="download">查看病假原始材料</el-button><p v-if="reviewer">请核对姓名、就诊日期、医嘱与申请期限后，再作出审核决定。</p></div></template>
<script setup lang="ts">
import { ref } from 'vue'
import { http } from '../api/http'
const props=defineProps<{id:number;reviewer?:boolean}>();const busy=ref(false)
async function download(){busy.value=true;try{const r=await http.get(`/leave/${props.id}/medical-record`,{responseType:'blob'});const type=r.data.type;const ext=type.includes('pdf')?'pdf':type.includes('png')?'png':'jpg';const url=URL.createObjectURL(r.data),link=document.createElement('a');link.href=url;link.download=`病假材料-${props.id}.${ext}`;link.click();setTimeout(()=>URL.revokeObjectURL(url),1000)}finally{busy.value=false}}
</script>
<style scoped>.medical-review{background:#f1f8f4;border:1px solid #c7dfd2;padding:12px;margin:10px 0;border-radius:8px}</style>

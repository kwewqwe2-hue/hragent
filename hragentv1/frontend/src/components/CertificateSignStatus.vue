<template>
 <div class="sign-status"><strong>{{ state?.label || '正在读取签章状态…' }}</strong>
  <p v-if="state?.error">{{ state.error }}</p>
  <el-button v-if="state?.signed" type="success" @click="download">下载已盖章 PDF</el-button>
  <el-button v-if="hr && state && ['NOT_STARTED','WAITING_CONFIG','FAILED'].includes(state.status) && !state.flowId" :loading="busy" @click="start">发起电子签章</el-button>
  <template v-if="hr && state?.status === 'UNCERTAIN'"><el-input v-model="flowId" placeholder="腾讯电子签中对应证明的流程 ID" /><el-button :loading="busy" @click="reconcile">核对并关联流程</el-button></template>
  <p v-if="error" role="alert">{{ error }}</p>
 </div>
</template>
<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { getData, postData, putData, downloadBinary } from '../api/http'
const props=defineProps<{id:number;hr:boolean}>()
const state=ref<{status:string;label:string;error:string;signed:boolean;flowId:string}>(),busy=ref(false),error=ref(''),flowId=ref('');let timer:ReturnType<typeof setTimeout>|undefined,alive=true
async function load(){try{const data=await getData<NonNullable<typeof state.value>>(`/employment-certificates/${props.id}/esign`);if(alive)state.value=data}catch{if(alive)error.value='签章状态读取失败'}finally{if(alive)timer=setTimeout(load,15000)}}
onMounted(load);onBeforeUnmount(()=>{alive=false;if(timer)clearTimeout(timer)})
async function start(){busy.value=true;try{state.value=await postData(`/employment-certificates/${props.id}/esign`);error.value=''}catch{error.value='请先完成电子签设置，再重新发起'}finally{busy.value=false}}
async function reconcile(){busy.value=true;try{state.value=await putData(`/employment-certificates/${props.id}/esign/reconcile`,{flowId:flowId.value});error.value=''}catch{error.value='流程关联失败，请核对腾讯电子签中的证明编号与流程 ID'}finally{busy.value=false}}
async function download(){try{await downloadBinary(`/employment-certificates/${props.id}/signed-document`,`在职证明-${props.id}-已盖章.pdf`)}catch{error.value='文件正在合成或下载失败，请稍后重试'}}
</script>
<style scoped>.sign-status{grid-column:1/-1;padding:14px;border:1px solid #c7dfd5;border-radius:10px}.sign-status p{font-size:13px;line-height:1.6}</style>

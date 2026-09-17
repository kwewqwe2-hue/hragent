<template>
 <section class="template-card">
  <strong>{{ state?.uploaded ? '模板已读取，补齐后即可提交' : '证明模板' }}</strong>
  <p v-if="loading" role="status">正在读取已保存的模板和员工档案…</p>
  <p v-else-if="!state && disabled">模板办理记录，请在最新回复中继续操作。</p>
  <template v-if="state?.uploaded">
   <div class="saved-template"><span>✓ {{ state.fileName }}</span><button v-if="!disabled" type="button" class="replace" @click="replacing=!replacing">{{ replacing ? '保留当前模板' : '更换模板' }}</button></div>
   <p>文件语言：{{ state.language }}。审核通过后按当前模板生成正式证明。</p>
   <details v-if="Object.keys(state.automatic || {}).length"><summary>已从档案及申请信息带入 {{ Object.keys(state.automatic || {}).length }} 项</summary><dl><template v-for="(value,key) in state.automatic" :key="key"><dt>{{ fieldLabels[key] || key }}</dt><dd>{{ key === 'passportNumber' ? '已从档案读取' : value }}</dd></template></dl></details>
   <p v-if="!missingFields.length">所需信息已齐全，无需重复填写，直接提交审核就好。</p>
   <label v-for="field in missingFields" :key="field.key">{{ field.label }}<small v-if="field.profileSupplement">档案暂缺，本次补充将交由 HR 核实</small><input v-model="values[field.key]" :type="field.key.endsWith('Date') ? 'date' : 'text'" :aria-label="field.label" :disabled="disabled || busy" maxlength="1000" @input="error=''" /></label>
   <button class="submit" :disabled="disabled || busy || loading" @click="confirm">{{ busy ? '正在提交…' : '提交 HR 审核' }}</button>
  </template>
  <div v-if="!disabled && (state?.uploaded === false || replacing)" class="replacement"><p>选择 DOCX 模板（最大 5 MB）。</p><input aria-label="上传在职证明模板" type="file" accept=".docx" :disabled="busy" @change="upload" /></div>
  <p v-if="error" role="alert" class="error">{{ error }}</p>
  <button v-if="error && !state && !disabled" type="button" @click="load">重新读取模板</button>
 </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { apiErrorMessage, serviceRequest } from '../api'
import type { AuthSession, ChatAction } from '../types'
const props=defineProps<{session:AuthSession;id:string;label:string;disabled:boolean}>()
const emit=defineEmits<{result:[response:{answer:string;provider?:string;actions?:ChatAction[]}];busy:[boolean]}>()
type MissingField={key:string;label:string;profileSupplement:boolean}
const state=ref<{uploaded:boolean;fileName?:string;automatic?:Record<string,string>;missingFields?:MissingField[];values?:Record<string,string>;language?:string} | null>(null)
const values=reactive<Record<string,string>>({}),busy=ref(false),loading=ref(false),replacing=ref(false),error=ref('');let alive=true
const missingFields=computed(()=>state.value?.missingFields || [])
const fieldLabels:Record<string,string>={legalName:'姓名',englishName:'英文姓名',employeeNo:'工号',department:'部门',title:'职位',entryDate:'入职日期',passportNumber:'护照号码',passportExpiryDate:'护照有效期',monthlySalary:'月薪',currency:'币种',companyName:'公司名称',issueDate:'开具日期',purpose:'用途',destinationCountry:'目的国家',consulateName:'受理机构'}
onBeforeUnmount(()=>{alive=false})
async function load(){if(props.disabled||loading.value)return;loading.value=true;error.value='';try{const data=await serviceRequest(props.session,`/lifecycle/${props.id}/certificate-template`);if(alive){state.value=data;Object.assign(values,data.values||{})}}catch(e){if(alive)error.value=apiErrorMessage(e)}finally{loading.value=false}}
onMounted(load)
watch(()=>props.disabled,disabled=>{if(!disabled&&!state.value)void load()})
async function act(method:string,body:unknown){if(props.disabled||busy.value)return;const session=props.session;busy.value=true;emit('busy',true);error.value='';try{const r=await serviceRequest(session,`/lifecycle/${props.id}/certificate-template`,{method,body});if(alive&&session===props.session)emit('result',r)}catch(e){if(alive)error.value=apiErrorMessage(e)}finally{busy.value=false;emit('busy',false)}}
async function upload(event:Event){const input=event.target as HTMLInputElement,file=input.files?.[0];input.value='';if(!file)return;if(!file.name.toLowerCase().endsWith('.docx')||file.size===0||file.size>5*1024*1024){error.value='请选择非空且不超过 5 MB 的 DOCX 文件';return}const data=new FormData();data.append('file',file);await act('POST',data)}
async function confirm(){if(missingFields.value.some(f=>!values[f.key]?.trim())){error.value='还差一点点，请补齐上面的缺失项再提交哦';return}await act('PUT',Object.fromEntries(missingFields.value.map(f=>[f.key,values[f.key]])))}
</script>
<style scoped>
.template-card{flex:0 1 100%;width:100%;box-sizing:border-box;padding:18px;border:1px solid #c6ded8;border-radius:14px;background:#f4faf8}.template-card p,.template-card small{font-size:13px;line-height:1.7;color:#49615c}.template-card label{display:grid;gap:6px;margin:12px 0}.template-card input{width:100%;max-width:100%;box-sizing:border-box;padding:10px;border:1px solid #c6ded8;border-radius:8px;background:white}.template-card button{align-self:start;background:#166e60;color:white;border:1px solid #166e60;border-radius:8px;padding:10px 18px;cursor:pointer}.template-card button:disabled{opacity:.5;cursor:default}.template-card .error{color:#a13d32}.saved-template{display:flex;align-items:center;justify-content:space-between;gap:12px;margin:14px 0;overflow-wrap:anywhere}.template-card .replace{flex-shrink:0;background:transparent;color:#166e60;padding:6px 10px;font-size:12px}.template-card summary{cursor:pointer;color:#32685c;font-size:13px}.template-card dl{display:grid;grid-template-columns:100px 1fr;font-size:13px;gap:8px}.template-card dd{margin:0;overflow-wrap:anywhere}.submit{margin-top:12px}.replacement{margin-top:12px}
</style>

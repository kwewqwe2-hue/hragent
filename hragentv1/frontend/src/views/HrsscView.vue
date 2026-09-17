<template>
 <div><div class="page-title"><div><h1>HRSSC 服务办理</h1><p>复核员工在线申请、回复处理意见并交付文件。</p></div><el-button :loading="loading" @click="load">刷新</el-button></div>
 <el-alert v-if="error" :title="error" type="error" :closable="false" />
 <div v-if="auth.user?.role === 'HR'" class="content-panel">
  <el-tabs v-model="tab"><el-tab-pane label="服务申请" name="cases"/><el-tab-pane label="发布工资明细" name="pay"/></el-tabs>
  <template v-if="tab==='cases'"><el-select v-model="filter" aria-label="申请状态"><el-option label="全部" value=""/><el-option v-for="(label,value) in statuses" :key="value" :label="label" :value="value"/></el-select>
   <el-table :data="visible" stripe empty-text="暂无服务申请"><el-table-column prop="id" label="单号" width="80"/><el-table-column prop="employeeName" label="员工"/><el-table-column prop="label" label="事项"/><el-table-column prop="statusLabel" label="进度"/><el-table-column label="操作"><template #default="{row}"><el-button @click="select(row)">查看处理</el-button></template></el-table-column></el-table>
  </template>
  <el-form v-else label-position="top" @submit.prevent="publish"><el-form-item label="员工"><el-select v-model="pay.employeeId" filterable placeholder="按姓名或工号选择"><el-option v-for="person in employees" :key="person.id" :label="`${person.name} · ${person.employeeNo}`" :value="person.id"/></el-select></el-form-item><el-form-item label="工资月份"><el-date-picker v-model="pay.month" type="month" value-format="YYYY-MM"/></el-form-item><el-form-item label="已核对的工资明细"><el-input v-model="pay.details" type="textarea" :rows="8" maxlength="4000" placeholder="填写应发项目、各项扣款、实发金额及币种。仅发布真实核对数据。"/></el-form-item><p>发布后，该员工可在助手中询问相应月份的工资条。再次发布同一月份会更新原记录。</p><el-button type="primary" native-type="submit" :loading="busy">确认发布</el-button></el-form>
 </div>
 <el-empty v-else description="此页面仅供 HRSSC 管理员处理申请。员工可在助手中查看自己的服务单。"/>
 <el-dialog v-model="open" title="处理服务申请" width="min(680px, 94vw)"><template v-if="selected"><h3>#{{ selected.id }} · {{ selected.label }} · {{ selected.employeeName }}</h3><p>当前状态：{{ selected.statusLabel }}</p><dl><template v-for="(value,key) in selected.fields" :key="key"><dt>{{ key }}</dt><dd>{{ value }}</dd></template></dl><el-button v-if="selected.materialsName" @click="downloadBinary(`/lifecycle/hr/${selected.id}/materials`,selected.materialsName)">下载申请材料</el-button><p v-if="selected.opinion">上次回复：{{ selected.opinion }}</p>
  <template v-if="selected.certificateRequestId"><el-alert title="该申请复用原有证明审核、模板及文件生成流程。" type="info" :closable="false"/><router-link to="/certificates">打开证明管理</router-link></template><template v-else-if="selected.leaveRequestId"><el-alert title="该申请沿用主管审批及 HR 备案，扣减额度由原流程完成。" type="info" :closable="false"/><router-link to="/hr-record">打开 HR 请假备案</router-link></template>
  <template v-else><label v-if="['SUBMITTED','APPROVED'].includes(selected.status)">处理意见<el-input v-model="opinion" type="textarea" :rows="4" maxlength="2000" placeholder="说明处理结果、补充要求或交付说明"/></label>
  <div v-if="selected.status==='APPROVED'" class="upload"><label>交付 PDF（不超过 5 MB）<input type="file" accept="application/pdf,.pdf" @change="upload" :disabled="busy"/></label><p>{{ fileNotice || '证明类申请必须上传经核实的正式文件后才能办结。' }}</p></div>
  <div class="actions"><el-button v-if="selected.status==='SUBMITTED'" type="primary" :loading="busy" @click="review('APPROVED')">审核通过，开始办理</el-button><el-button v-if="['SUBMITTED','APPROVED'].includes(selected.status)" :disabled="busy" @click="review('NEEDS_INFO')">退回补充</el-button><el-button v-if="selected.status==='SUBMITTED'" :disabled="busy" @click="review('REJECTED')">不予通过</el-button><el-button v-if="selected.status==='APPROVED'" type="success" :loading="busy" @click="review('COMPLETED')">确认已办理并交付</el-button></div></template>
 </template></el-dialog></div>
</template>
<script setup lang="ts">
import {computed,onMounted,ref,reactive} from 'vue'
import {getData,postData,http,downloadBinary} from '../api/http'
import {useAuthStore} from '../stores/auth'
import {ElMessage} from 'element-plus'
const auth=useAuthStore(),rows=ref<any[]>([]),loading=ref(false),busy=ref(false),error=ref(''),open=ref(false),selected=ref<any>(null),opinion=ref(''),fileNotice=ref(''),tab=ref('cases'),filter=ref('')
const employees=ref<Array<{id:number;name:string;employeeNo:string}>>([])
const pay=reactive({employeeId:undefined as number|undefined,month:'',details:''})
const statuses={SUBMITTED:'待审核',NEEDS_INFO:'待补充',APPROVED:'办理中',COMPLETED:'已办结',REJECTED:'未通过',CANCELLED:'已取消'}
const visible=computed(()=>rows.value.filter(r=>!filter.value||r.status===filter.value))
async function load(){if(auth.user?.role!=='HR')return;loading.value=true;error.value='';try{const result=await Promise.all([getData<any[]>('/lifecycle/hr/queue'),getData<Array<{id:number;name:string;employeeNo:string}>>('/admin/employees')]);rows.value=result[0];employees.value=result[1]}catch(e:any){error.value=e.response?.data?.message||'加载失败，请重试'}finally{loading.value=false}}
function select(row:any){selected.value=row;opinion.value='';fileNotice.value='';open.value=true}
async function run(action:()=>Promise<void>){if(busy.value)return;busy.value=true;try{await action()}catch(e:any){ElMessage.error(e.response?.data?.message||e.message||'操作失败')}finally{busy.value=false}}
async function review(action:string){if(!opinion.value.trim()){ElMessage.warning('请填写处理意见');return}await run(async()=>{selected.value=await postData(`/lifecycle/hr/${selected.value.id}/review`,{action,opinion:opinion.value});ElMessage.success('处理结果已更新');await load()})}
async function upload(event:Event){const file=(event.target as HTMLInputElement).files?.[0];if(!file)return;await run(async()=>{const body=new FormData();body.append('file',file);await http.post(`/lifecycle/hr/${selected.value.id}/file`,body);fileNotice.value='文件已上传，办结后员工即可下载';})}
async function publish(){if(!pay.employeeId||!pay.month||!pay.details.trim()){ElMessage.warning('请填写员工、月份和明细');return}await run(async()=>{await postData('/lifecycle/hr/payslips',pay);ElMessage.success('工资明细已发布');pay.details=''})}
onMounted(load)
</script>
<style scoped>dl{background:#f6f8fb;padding:16px;border-radius:8px}dt{font-weight:600;margin-top:12px}dd{margin:6px 0;white-space:pre-wrap;overflow-wrap:anywhere}.actions{display:flex;gap:8px;flex-wrap:wrap;margin-top:20px}.upload{margin-top:20px}p{line-height:1.7;color:#667085}</style>

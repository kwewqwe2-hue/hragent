<template>
 <section class="content-panel esign-settings">
  <h2>腾讯电子签</h2>
  <p>HR 审核通过后，系统将证明转成 PDF，并使用公司电子印章盖章，完成后员工可下载。</p>
  <el-form label-position="top" @submit.prevent="save">
   <el-form-item label="企业名称"><el-input v-model="form.organizationName" maxlength="200" /></el-form-item>
   <el-form-item label="经办人 UserId"><el-input v-model="form.operatorId" maxlength="200" /></el-form-item>
   <el-form-item label="公司电子印章 SealId"><el-input v-model="form.sealId" maxlength="200" /></el-form-item>
   <el-form-item label="盖章定位文字"><el-input v-model="form.sealKeyword" maxlength="60" /><small>模板中保留一处该文字，例如“公司盖章处”。</small></el-form-item>
   <el-form-item label="SecretId"><el-input v-model="form.secretId" type="password" autocomplete="off" :placeholder="hasCredentials ? '已保存，留空保留' : '腾讯云 API SecretId'" /></el-form-item>
   <el-form-item label="SecretKey"><el-input v-model="form.secretKey" type="password" autocomplete="new-password" :placeholder="hasCredentials ? '已保存，留空保留' : '腾讯云 API SecretKey'" /></el-form-item>
   <el-form-item label="审核通过后自动盖章"><el-switch v-model="form.enabled" /></el-form-item>
   <p>请在腾讯电子签企业控制台开通自动签署，并为经办人和应用配置相应的印章使用权限。</p>
   <el-button type="primary" :loading="busy" native-type="submit">保存设置</el-button>
  </el-form>
  <p v-if="error" role="alert">{{ error }}</p>
 </section>
</template>
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getData, putData } from '../api/http'
const form=reactive({enabled:false,organizationName:'',operatorId:'',sealId:'',sealKeyword:'公司盖章处',secretId:'',secretKey:''})
const busy=ref(false),hasCredentials=ref(false),error=ref('')
onMounted(async()=>{try{const data=await getData<typeof form & {hasCredentials:boolean}>('/employment-certificates/esign/config');Object.assign(form,data);hasCredentials.value=data.hasCredentials}catch{error.value='电子签设置加载失败，请刷新重试'}})
async function save(){busy.value=true;error.value='';try{await putData('/employment-certificates/esign/config',form);form.secretId='';form.secretKey='';hasCredentials.value=true;ElMessage.success('电子签设置已保存')}catch{error.value='保存失败，请检查配置内容'}finally{busy.value=false}}
</script>
<style scoped>.esign-settings{max-width:720px}.esign-settings p{line-height:1.8;color:#52635f}.esign-settings small{color:#64756f}</style>

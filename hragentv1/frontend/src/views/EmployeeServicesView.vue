<template><div class="services-page"><div class="page-title"><div><h1>员工服务中心</h1><p>新增智能服务统一入口，既有请假、证明、通讯录入口保持不变。</p></div></div><section><div class="section-heading"><h2>日常服务</h2><span>制度政策精准问答</span></div><button class="service-card" type="button" @click="go('/employee-experience?section=policy')"><el-icon><ChatDotRound /></el-icon><strong>Policy Copilot</strong><span>咨询考勤、年假、加班、差旅和福利等制度问题。</span><el-icon class="arrow"><ArrowRight /></el-icon></button></section><section><div class="section-heading"><h2>个人成长与关怀</h2><span>{{ isNewHire ? "入职导航、成长支持与关怀咨询" : "工作成长、协作支持与关怀咨询" }}</span></div><div class="service-grid"><button v-for="item in care" :key="item.title" class="service-card" type="button" @click="go(item.path)"><el-icon><component :is="item.icon" /></el-icon><strong>{{ item.title }}</strong><span>{{ item.description }}</span><el-icon class="arrow"><ArrowRight /></el-icon></button></div></section><section><div class="section-heading"><h2>合规与申诉</h2><span>敏感问题优先引导人工处理</span></div><button class="service-card" type="button" @click="go('/employee-experience?section=compliance')"><el-icon><WarningFilled /></el-icon><strong>合规咨询与人工转介</strong><span>咨询劳动关系、竞业、工伤等敏感问题；风险内容将转入人工处理流程。</span><el-icon class="arrow"><ArrowRight /></el-icon></button></section></div></template>
<script setup lang="ts">
import { ArrowRight, ChatDotRound, Postcard, WarningFilled } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { computed } from 'vue'
import { useAuthStore } from '../stores/auth'
const router = useRouter()
const go = (path: string) => router.push(path)
const auth = useAuthStore()
const isNewHire = computed(() => auth.user?.role === 'NEW_HIRE' || auth.user?.employeeStatus === 'ONBOARDING')
const care = computed(() => [
  { title: isNewHire.value ? '入职与成长导航' : '工作成长与协作', description: isNewHire.value ? '按阶段完成入职任务、了解团队并准备工具权限' : '安排每周工作与成长计划、理解协作用语并管理工具需求', icon: Postcard, path: '/employee-experience?section=onboarding' },
  { title: '支持与关怀咨询', description: '整理困扰、咨询助手并查找企业支持渠道', icon: ChatDotRound, path: '/employee-experience?section=support' }
])
</script>
<style scoped>.services-page section{margin-top:28px}.section-heading{display:flex;align-items:baseline;gap:12px;margin-bottom:12px}.section-heading h2{margin:0;color:#182230;font-size:18px}.section-heading span{color:#667085;font-size:13px}.service-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}.service-card{position:relative;width:100%;min-height:136px;display:flex;align-items:flex-start;flex-direction:column;gap:10px;padding:18px;color:#344054;background:#fff;border:1px solid #d9e0ea;border-radius:6px;text-align:left;cursor:pointer}.service-card:hover{border-color:#2f80ed;box-shadow:0 4px 12px rgba(47,128,237,.12)}.service-card>.el-icon:first-child{color:#2f80ed;font-size:22px}.service-card strong{font-size:15px}.service-card span{color:#667085;font-size:13px;line-height:1.55}.arrow{position:absolute;right:16px;bottom:16px;color:#2f80ed}@media(max-width:700px){.service-grid{grid-template-columns:1fr}.section-heading{align-items:flex-start;flex-direction:column;gap:3px}}</style>

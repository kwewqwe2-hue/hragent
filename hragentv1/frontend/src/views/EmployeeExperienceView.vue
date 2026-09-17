<template><EmployeeServices v-if="auth.user" :user="auth.user" :request="request" :workbench-url="workbenchUrl" :initial-tab="initialTab" /></template>
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import EmployeeServices from '../components/EmployeeServices.vue'
import { useAuthStore } from '../stores/auth'
import { http } from '../api/http'
const auth = useAuthStore(), route = useRoute()
const workbenchUrl = window.location.origin
const initialTab = computed(() => typeof route.query.section === 'string' && ['policy','care','onboarding','support','compliance','certificates','reminders','guides'].includes(route.query.section) ? route.query.section : 'policy')
async function request(path: string, options: { method?: string; body?: unknown; binary?: boolean } = {}) {
  const response = await http.request({ url: path, method: options.method || 'GET', data: options.body, responseType: options.binary ? 'blob' : 'json' })
  return options.binary ? response.data : response.data.data
}
</script>

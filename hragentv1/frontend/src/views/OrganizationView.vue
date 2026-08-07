<template>
  <div>
    <div class="page-title">
      <div>
        <h1>组织配置</h1>
        <p>维护员工导入和账号管理依赖的部门、岗位基础数据。</p>
      </div>
    </div>

    <section class="content-panel">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="部门" name="departments">
          <div class="toolbar-row">
            <strong>部门列表</strong>
            <el-button type="primary" :icon="Plus" @click="openDepartment()">新增部门</el-button>
          </div>
          <el-table :data="departments" stripe>
            <el-table-column prop="name" label="部门名称" />
            <el-table-column prop="code" label="编码" width="130" />
            <el-table-column prop="description" label="描述" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.active ? 'success' : 'info'">{{ row.active ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button size="small" :icon="Edit" @click="openDepartment(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="岗位" name="jobTitles">
          <div class="toolbar-row">
            <strong>岗位列表</strong>
            <el-button type="primary" :icon="Plus" @click="openJobTitle()">新增岗位</el-button>
          </div>
          <el-table :data="jobTitles" stripe>
            <el-table-column prop="name" label="岗位名称" />
            <el-table-column prop="code" label="编码" width="130" />
            <el-table-column prop="description" label="描述" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.active ? 'success' : 'info'">{{ row.active ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button size="small" :icon="Edit" @click="openJobTitle(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-dialog v-model="visible" :title="editingId ? '编辑配置' : '新增配置'" width="560px">
      <el-form :model="form" label-position="top">
        <el-form-item label="名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="编码">
          <el-input v-model="form.code" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.active" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Edit, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getData, postData, putData } from '../api/http'
import type { Department, JobTitle } from '../api/types'

const activeTab = ref('departments')
const departments = ref<Department[]>([])
const jobTitles = ref<JobTitle[]>([])
const visible = ref(false)
const editingId = ref<number | null>(null)
const editingType = ref<'department' | 'jobTitle'>('department')
const form = reactive({
  name: '',
  code: '',
  description: '',
  active: true
})

async function load() {
  departments.value = await getData('/admin/departments')
  jobTitles.value = await getData('/admin/job-titles')
}

function openDepartment(row?: Department) {
  editingType.value = 'department'
  editingId.value = row?.id || null
  Object.assign(form, row || { name: '', code: '', description: '', active: true })
  visible.value = true
}

function openJobTitle(row?: JobTitle) {
  editingType.value = 'jobTitle'
  editingId.value = row?.id || null
  Object.assign(form, row || { name: '', code: '', description: '', active: true })
  visible.value = true
}

async function save() {
  const base = editingType.value === 'department' ? '/admin/departments' : '/admin/job-titles'
  if (editingId.value) {
    await putData(`${base}/${editingId.value}`, form)
  } else {
    await postData(base, form)
  }
  ElMessage.success('保存成功')
  visible.value = false
  await load()
}

onMounted(load)
</script>

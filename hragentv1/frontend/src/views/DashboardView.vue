<template>
  <div>
    <div class="page-title">
      <div>
        <h1>工作台</h1>
        <p>查看个人假期余额、审批待办和真实日期请假日历。</p>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <div class="stat-grid">
      <div class="stat-card"><div class="label">待主管审批</div><div class="value">{{ stats.pendingManager || 0 }}</div></div>
      <div class="stat-card"><div class="label">待管理员备案</div><div class="value">{{ stats.pendingHr || 0 }}</div></div>
      <div class="stat-card"><div class="label">已通过</div><div class="value">{{ stats.approved || 0 }}</div></div>
      <div class="stat-card"><div class="label">已驳回</div><div class="value">{{ stats.rejected || 0 }}</div></div>
    </div>

    <section class="content-panel heatmap-panel">
      <div class="toolbar-row">
        <div>
          <strong>年度请假轨迹</strong>
          <span class="muted heatmap-caption">每个方格代表一个真实日期</span>
        </div>
        <el-select v-model="selectedYear" class="year-select" @change="changeYear">
          <el-option v-for="year in yearOptions" :key="year" :label="`${year} 年`" :value="year" />
        </el-select>
      </div>
      <div class="heatmap-scroll">
        <div class="weekday-labels">
          <span>周一</span><span>周二</span><span>周三</span><span>周四</span><span>周五</span><span>周六</span><span>周日</span>
        </div>
        <div class="heatmap-grid">
          <el-tooltip
            v-for="cell in heatmapCells"
            :key="cell.day.date"
            :content="`${cell.day.date} · ${cell.day.label}`"
            placement="top"
          >
            <span
              :class="['heat-cell', `is-${cell.day.dayType.toLowerCase()}`]"
              :style="{ gridRow: cell.row, gridColumn: cell.column }"
            />
          </el-tooltip>
        </div>
      </div>
      <div class="calendar-legend">
        <span><i class="legend-work" />上班</span>
        <span><i class="legend-rest" />周末休息</span>
        <span><i class="legend-pending" />审批中</span>
        <span><i class="legend-leave" />已批准休假</span>
      </div>
    </section>

    <div class="dashboard-grid">
      <section class="content-panel calendar-panel">
        <div class="toolbar-row">
          <strong>{{ auth.user?.name }}的请假日历</strong>
          <span class="muted">最终备案通过后自动变为休假</span>
        </div>
        <el-calendar v-model="calendarDate">
          <template #date-cell="{ data }">
            <div :class="['calendar-cell', calendarDay(data.day)?.dayType.toLowerCase()]">
              <span class="date-number">{{ Number(data.day.slice(-2)) }}</span>
              <span class="date-status">{{ calendarDay(data.day)?.label || '' }}</span>
            </div>
          </template>
        </el-calendar>
      </section>

      <div class="side-column">
        <section class="content-panel">
          <div class="toolbar-row"><strong>我的假期余额</strong></div>
          <el-table :data="balances" stripe>
            <el-table-column prop="leaveTypeLabel" label="假别" />
            <el-table-column prop="totalDays" label="总额" width="72" />
            <el-table-column prop="usedDays" label="已用" width="72" />
            <el-table-column prop="remainingDays" label="剩余" width="72" />
          </el-table>
        </section>

        <section class="content-panel request-panel">
          <div class="toolbar-row"><strong>{{ listTitle }}</strong></div>
          <el-table :data="requests" stripe empty-text="暂无待办或申请">
            <el-table-column prop="employeeName" label="员工" width="90" />
            <el-table-column prop="leaveTypeLabel" label="假别" width="80" />
            <el-table-column label="日期" min-width="112">
              <template #default="{ row }">{{ row.startDate }}<br />{{ row.endDate }}</template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <span :class="['status-pill', statusClass(row.status)]">{{ row.statusLabel }}</span>
              </template>
            </el-table-column>
          </el-table>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getData } from '../api/http'
import type { LeaveBalance, LeaveCalendar, LeaveCalendarDay, LeaveRequest } from '../api/types'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const currentYear = new Date().getFullYear()
const stats = ref<Record<string, number>>({})
const balances = ref<LeaveBalance[]>([])
const requests = ref<LeaveRequest[]>([])
const ownRequests = ref<LeaveRequest[]>([])
const selectedYear = ref(currentYear)
const calendarDate = ref(new Date())
const calendar = ref<LeaveCalendar>({ year: currentYear, days: [] })
const yearOptions = [currentYear - 1, currentYear, currentYear + 1]
let refreshTimer: number | undefined

const listTitle = computed(() => {
  if (auth.user?.role === 'MANAGER') return '我的审批待办'
  if (auth.user?.role === 'HR') return '管理员备案待办'
  return '我的申请记录'
})

interface HeatmapCell {
  day: LeaveCalendarDay
  row: number
  column: number
}

const calendarMap = computed(() => {
  const days = new Map(calendar.value.days.map((day) => [day.date, { ...day }]))
  const applicableRequests = ownRequests.value
          .filter((request) => request.status !== 'REJECTED')
          .sort((first, second) => statusPriority(first.status) - statusPriority(second.status))

  for (const request of applicableRequests) {
    const cursor = new Date(`${request.startDate}T12:00:00`)
    const end = new Date(`${request.endDate}T12:00:00`)
    while (cursor <= end) {
      const dayOfWeek = cursor.getDay()
      if (dayOfWeek !== 0 && dayOfWeek !== 6) {
        const date = formatLocalDate(cursor)
        if (date.startsWith(`${selectedYear.value}-`)) {
          const approved = request.status === 'APPROVED'
          days.set(date, {
            date,
            dayType: approved ? 'LEAVE' : 'PENDING',
            label: approved ? request.leaveTypeLabel : `${request.leaveTypeLabel}审批中`,
            leaveType: request.leaveType,
            leaveTypeLabel: request.leaveTypeLabel,
            requestStatus: request.status
          })
        }
      }
      cursor.setDate(cursor.getDate() + 1)
    }
  }
  return days
})

const heatmapCells = computed<HeatmapCell[]>(() => {
  if (!calendar.value.days.length) return []
  const first = new Date(`${selectedYear.value}-01-01T12:00:00`)
  const mondayOffset = (first.getDay() + 6) % 7
  return calendar.value.days.map((sourceDay, index) => {
    const position = mondayOffset + index
    return {
      day: calendarMap.value.get(sourceDay.date) || sourceDay,
      row: position % 7 + 1,
      column: Math.floor(position / 7) + 1
    }
  })
})

function statusPriority(status: string) {
  return status === 'APPROVED' ? 2 : 1
}

function formatLocalDate(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function calendarDay(date: string) {
  const existing = calendarMap.value.get(date)
  if (existing) return existing
  const value = new Date(`${date}T00:00:00`)
  const weekend = value.getDay() === 0 || value.getDay() === 6
  return {
    date,
    dayType: weekend ? 'REST' : 'WORK',
    label: weekend ? '休息' : '上班'
  } as LeaveCalendarDay
}

function statusClass(status: string) {
  if (status === 'APPROVED') return 'approved'
  if (status === 'REJECTED') return 'rejected'
  return 'pending'
}

async function loadCalendar(year: number) {
  calendar.value = await getData<LeaveCalendar>(`/leave/calendar?year=${year}&_t=${Date.now()}`)
}

async function refreshLeaveSnapshot() {
  const [, balanceData, ownRequestData] = await Promise.all([
    loadCalendar(selectedYear.value),
    getData<LeaveBalance[]>(`/leave/balances?_t=${Date.now()}`),
    getData<LeaveRequest[]>(`/leave/my?_t=${Date.now()}`)
  ])
  balances.value = balanceData
  ownRequests.value = ownRequestData
}

async function changeYear(year: number) {
  const month = year === currentYear ? new Date().getMonth() : 0
  calendarDate.value = new Date(year, month, 1)
  await loadCalendar(year)
}

async function load() {
  const ownRequestPromise = getData<LeaveRequest[]>(`/leave/my?_t=${Date.now()}`)
  const [statsData, balanceData, requestData, ownRequestData] = await Promise.all([
    getData<Record<string, number>>('/leave/stats'),
    getData<LeaveBalance[]>('/leave/balances'),
    auth.user?.role === 'MANAGER'
      ? getData<LeaveRequest[]>('/leave/manager/pending')
      : auth.user?.role === 'HR'
        ? getData<LeaveRequest[]>('/leave/hr/pending')
        : ownRequestPromise,
    ownRequestPromise
  ])
  stats.value = statsData
  balances.value = balanceData
  requests.value = requestData
  ownRequests.value = ownRequestData
  await loadCalendar(selectedYear.value)
}

watch(calendarDate, async (value) => {
  const year = value.getFullYear()
  if (year !== selectedYear.value) {
    selectedYear.value = year
    await loadCalendar(year)
  }
})

async function refreshWhenVisible() {
  if (document.visibilityState === 'visible') await load()
}

onMounted(async () => {
  await load()
  document.addEventListener('visibilitychange', refreshWhenVisible)
  window.addEventListener('focus', refreshWhenVisible)
  refreshTimer = window.setInterval(() => {
    if (document.visibilityState === 'visible') refreshLeaveSnapshot()
  }, 15000)
})

onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', refreshWhenVisible)
  window.removeEventListener('focus', refreshWhenVisible)
  if (refreshTimer) window.clearInterval(refreshTimer)
})
</script>

<style scoped>
.heatmap-panel {
  margin-bottom: 16px;
}

.heatmap-caption {
  margin-left: 10px;
  font-size: 12px;
}

.year-select {
  width: 120px;
}

.heatmap-scroll {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding: 4px 0 8px;
}

.weekday-labels {
  display: grid;
  grid-template-rows: repeat(7, 12px);
  gap: 4px;
}

.weekday-labels {
  flex: 0 0 30px;
  color: #98a2b3;
  font-size: 10px;
  line-height: 12px;
}

.heatmap-grid {
  display: grid;
  grid-template-rows: repeat(7, 12px);
  grid-template-columns: repeat(54, 12px);
  gap: 4px;
  min-width: 844px;
}

.heat-cell {
  width: 12px;
  height: 12px;
  border: 1px solid rgba(16, 24, 40, 0.06);
  border-radius: 2px;
}

.is-work,
.legend-work {
  background: #dce6f3;
}

.is-rest,
.legend-rest {
  background: #e4e7ec;
}

.is-pending,
.legend-pending {
  background: #f5b942;
}

.is-leave,
.legend-leave {
  background: #32a071;
}

.calendar-legend {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 14px;
  color: #667085;
  font-size: 12px;
}

.calendar-legend span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.calendar-legend i {
  width: 11px;
  height: 11px;
  border-radius: 2px;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(540px, 1.45fr) minmax(360px, 0.8fr);
  gap: 16px;
}

.side-column {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.request-panel {
  min-height: 320px;
}

.calendar-panel {
  min-width: 0;
}

.calendar-cell {
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 4px;
  padding: 5px;
  border-left: 3px solid transparent;
}

.calendar-cell.work {
  border-left-color: #9eb6d2;
}

.calendar-cell.rest {
  color: #667085;
  background: #f2f4f7;
  border-left-color: #c7cdd6;
}

.calendar-cell.pending {
  color: #7a4f01;
  background: #fff6dc;
  border-left-color: #f5b942;
}

.calendar-cell.leave {
  color: #116149;
  background: #e5f5ed;
  border-left-color: #32a071;
}

.date-number {
  font-weight: 700;
}

.date-status {
  overflow: hidden;
  font-size: 11px;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.el-calendar) {
  --el-calendar-cell-width: 74px;
}

:deep(.el-calendar__body) {
  padding: 8px 0 0;
}

:deep(.el-calendar-table .el-calendar-day) {
  height: 86px;
  padding: 2px;
}

@media (max-width: 1180px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 700px) {
  .calendar-panel {
    overflow-x: auto;
  }

  :deep(.el-calendar) {
    min-width: 650px;
  }

  .heatmap-caption {
    display: block;
    margin: 4px 0 0;
  }
}
</style>

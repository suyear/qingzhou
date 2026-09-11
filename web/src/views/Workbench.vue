<template>
  <div v-loading="loading">
    <PageHeader title="工作台" desc="从编排、调度到开放调用的总览。先配组件和凭证，再画工作流。" />
    <PageState :error="loadError" @retry="load" />
    <div class="stat-grid">
      <button class="stat-card" @click="$router.push('/components')">
        <div class="stat-label">接口组件</div>
        <div class="stat-num">{{ displayNum(stats.components) }}</div>
      </button>
      <button class="stat-card" @click="$router.push('/workflows')">
        <div class="stat-label">工作流</div>
        <div class="stat-num">{{ displayNum(stats.workflows) }}</div>
      </button>
      <button class="stat-card" title="按最近执行记录估算" @click="$router.push('/executions')">
        <div class="stat-label">今日执行</div>
        <div class="stat-num">{{ displayNum(stats.today) }}</div>
        <div class="stat-hint">最近 100 条估算</div>
      </button>
      <button class="stat-card" @click="$router.push('/schedules')">
        <div class="stat-label">运行中的调度</div>
        <div class="stat-num">{{ displayNum(stats.runningJobs) }}</div>
      </button>
    </div>
    <div class="quick">
      <el-button type="primary" @click="$router.push('/components')">接入组件</el-button>
      <el-button @click="$router.push('/designer')">新建编排</el-button>
      <el-button @click="$router.push('/credentials')">配凭证</el-button>
      <el-button @click="$router.push('/openapi')">开放平台</el-button>
    </div>
    <el-alert
      v-if="!loading && !loadError && !stats.components"
      class="guide-alert"
      type="info"
      show-icon
      :closable="false"
      title="还没有接口组件"
      description="建议先接入一个 HTTP 接口（或体验预置企微组件），再去设计器拖成工作流。"
    >
      <el-button type="primary" size="small" @click="$router.push('/components')">去接入</el-button>
    </el-alert>
    <div class="qz-panel recent">
      <div class="recent-head">
        <h3>最近运行</h3>
        <el-button type="primary" link @click="$router.push('/executions')">全部记录</el-button>
      </div>
      <el-table
        v-if="recent.length"
        class="qz-table"
        :data="recent"
        size="small"
        stripe
        style="cursor: pointer"
        @row-click="goRecent"
      >
        <el-table-column prop="executionNo" label="单号" min-width="180" />
        <el-table-column label="工作流" min-width="140">
          <template #default="{ row }">{{ row.workflowName || row.workflowId }}</template>
        </el-table-column>
        <el-table-column label="触发" width="100">
          <template #default="{ row }">{{ triggerLabel(row.triggerType) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="execStatusType(row.status)">{{ execStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" min-width="160">
          <template #default="{ row }">{{ formatTime(row.startTime) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-else-if="!loading && !loadError" description="还没有执行记录，去设计器试运行一条工作流">
        <el-button type="primary" @click="$router.push('/workflows')">去编排</el-button>
      </el-empty>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import PageState from '@/components/PageState.vue'
import { pageComponents } from '@/api/component'
import { pageWorkflows } from '@/api/workflow'
import { pageExecutions } from '@/api/execution'
import { pageScheduleJobs } from '@/api/schedule'
import { networkErrorMessage } from '@/api/http'
import { execStatusLabel, execStatusType, formatTime, triggerLabel } from '@/utils/format'

const router = useRouter()
const loading = ref(false)
const loadError = ref('')
const recent = ref([])
const stats = reactive({
  components: null,
  workflows: null,
  today: null,
  runningJobs: null,
})

function displayNum(value) {
  return value == null ? '—' : value
}

function isToday(value) {
  if (!value) return false
  const day = String(value).slice(0, 10)
  const now = new Date()
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  return day === `${y}-${m}-${d}`
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const [comps, wfs, execs, jobs] = await Promise.all([
      pageComponents({ current: 1, size: 1 }),
      pageWorkflows({ current: 1, size: 1 }),
      pageExecutions({ current: 1, size: 100 }),
      pageScheduleJobs({ current: 1, size: 100 }),
    ])
    stats.components = Number(comps.data?.total || 0)
    stats.workflows = Number(wfs.data?.total || 0)
    const execRecords = execs.data?.records || []
    recent.value = execRecords.slice(0, 5)
    stats.today = execRecords.filter((item) => isToday(item.startTime || item.createTime)).length
    stats.runningJobs = (jobs.data?.records || []).filter((item) => item.status === 1).length
  } catch (error) {
    loadError.value = networkErrorMessage(error)
    recent.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)

function goRecent(row) {
  router.push({ path: '/executions', query: { keyword: row.executionNo } })
}
</script>

<style scoped>
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.stat-card {
  text-align: left;
  border: 1px solid var(--qz-border);
  background: var(--qz-card);
  border-radius: var(--qz-radius);
  box-shadow: var(--qz-shadow);
  padding: 16px 18px;
  cursor: pointer;
}
.stat-card:hover {
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 0 0 3px var(--qz-primary-soft);
}
.stat-label {
  color: var(--qz-text-muted);
  font-size: 13px;
}
.stat-num {
  margin-top: 6px;
  font-size: 28px;
  font-weight: 700;
  color: var(--qz-text);
}
.stat-hint {
  margin-top: 4px;
  font-size: 11px;
  color: var(--qz-text-muted);
}
.quick {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
.guide-alert {
  margin-bottom: 16px;
}
.recent-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px 0;
}
.recent :deep(.el-empty) {
  padding: 24px 16px 32px;
}
.recent-head h3 {
  margin: 0;
  font-size: 15px;
}
@media (max-width: 960px) {
  .stat-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>

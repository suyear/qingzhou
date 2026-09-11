<template>
  <div>
    <PageHeader title="工作台" desc="从编排、调度到开放调用的总览。先配组件和凭证，再画工作流。">
      <el-radio-group v-model="days" size="small" @change="loadDashboard">
        <el-radio-button :value="7">近 7 日</el-radio-button>
        <el-radio-button :value="14">近 14 日</el-radio-button>
        <el-radio-button :value="30">近 30 日</el-radio-button>
      </el-radio-group>
    </PageHeader>
    <PageState :error="loadError" @retry="load" />
    <div class="stat-grid" v-loading="loading && !overview">
      <button class="stat-card" @click="$router.push('/components')">
        <div class="stat-label">接口组件</div>
        <div class="stat-num">{{ displayNum(summary.components) }}</div>
      </button>
      <button class="stat-card" @click="$router.push('/workflows')">
        <div class="stat-label">工作流</div>
        <div class="stat-num">{{ displayNum(summary.workflows) }}</div>
      </button>
      <button class="stat-card" @click="$router.push('/executions')">
        <div class="stat-label">今日执行</div>
        <div class="stat-num">{{ displayNum(summary.todayExecutions) }}</div>
        <div class="stat-hint">{{ successHint }}</div>
      </button>
      <button class="stat-card stat-ok" @click="$router.push('/schedules')">
        <div class="stat-label">运行中的调度</div>
        <div class="stat-num">{{ displayNum(summary.runningJobs) }}</div>
      </button>
    </div>
    <div class="quick">
      <el-button type="primary" @click="$router.push('/components')">接入组件</el-button>
      <el-button @click="$router.push('/designer')">新建编排</el-button>
      <el-button @click="$router.push('/credentials')">配凭证</el-button>
      <el-button @click="$router.push('/openapi')">开放平台</el-button>
    </div>
    <el-alert
      v-if="!loading && !loadError && !summary.components"
      class="guide-alert"
      type="info"
      show-icon
      :closable="false"
      title="还没有接口组件"
      description="建议先接入一个 HTTP 接口（或体验预置企微组件），再去设计器拖成工作流。"
    >
      <el-button type="primary" size="small" @click="$router.push('/components')">去接入</el-button>
    </el-alert>

    <div class="qz-chart-grid">
      <ChartPanel
        title="执行次数趋势"
        :desc="`近 ${days} 日工作流执行量`"
        :option="trendChart"
        :loading="chartLoading"
        :error="chartError"
        :empty="!hasTrend"
        empty-text="这段时间还没有执行记录"
        @retry="loadDashboard"
      />
      <ChartPanel
        title="成功 / 失败占比"
        desc="仅统计已结束的执行"
        :option="statusChart"
        :loading="chartLoading"
        :error="chartError"
        :empty="!hasStatus"
        empty-text="暂无成功或失败数据"
        @retry="loadDashboard"
      />
      <ChartPanel
        title="触发类型分布"
        desc="试运行 / 调度 / 开放调用"
        :option="triggerChart"
        :loading="chartLoading"
        :error="chartError"
        :empty="!hasTrigger"
        empty-text="暂无触发记录"
        @retry="loadDashboard"
      />
      <ChartPanel
        title="热门工作流"
        desc="区间内执行次数 Top 5"
        :option="topWorkflowChart"
        :loading="chartLoading"
        :error="chartError"
        :empty="!hasTopWorkflows"
        empty-text="还没有可统计的工作流"
        @retry="loadDashboard"
      />
    </div>

    <div class="qz-panel recent">
      <div class="recent-head">
        <div>
          <h3>最近运行</h3>
          <p class="sub">点击行查看执行记录</p>
        </div>
        <el-button type="primary" link @click="$router.push('/executions')">全部记录</el-button>
      </div>
      <el-table
        v-if="recent.length"
        class="qz-table"
        :data="recent"
        stripe
        style="cursor: pointer"
        @row-click="goRecent"
      >
        <el-table-column prop="executionNo" label="单号" min-width="180" />
        <el-table-column label="工作流" min-width="140">
          <template #default="{ row }">{{ row.workflowName || row.workflowId }}</template>
        </el-table-column>
        <el-table-column label="触发" width="110">
          <template #default="{ row }">
            <StatusTag kind="trigger" :value="row.triggerType" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <StatusTag kind="exec" :value="row.status" />
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
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import PageState from '@/components/PageState.vue'
import ChartPanel from '@/components/ChartPanel.vue'
import StatusTag from '@/components/StatusTag.vue'
import { getDashboardOverview } from '@/api/dashboard'
import { pageExecutions } from '@/api/execution'
import { networkErrorMessage } from '@/api/http'
import { formatTime } from '@/utils/format'
import {
  STATUS_COLOR,
  TRIGGER_COLOR,
  barOption,
  hasChartData,
  rankBarOption,
  sharePieOption,
  trendOption,
} from '@/utils/charts'

const router = useRouter()
const days = ref(7)
const loading = ref(false)
const chartLoading = ref(false)
const loadError = ref('')
const chartError = ref('')
const recent = ref([])
const overview = ref(null)

const summary = computed(() => overview.value?.summary || {
  components: null,
  workflows: null,
  todayExecutions: null,
  runningJobs: null,
  successRate: null,
  rangeTotal: 0,
})

const successHint = computed(() => {
  const rate = summary.value.successRate
  if (rate == null) return '区间成功率 —'
  return `近 ${days.value} 日成功率 ${rate}%`
})

const hasTrend = computed(() => hasChartData(overview.value?.trend || [], 'total'))
const hasStatus = computed(() => hasChartData(overview.value?.statusShare))
const hasTrigger = computed(() => hasChartData(overview.value?.triggerShare))
const hasTopWorkflows = computed(() => hasChartData(overview.value?.topWorkflows || [], 'total'))

const trendChart = computed(() => trendOption(overview.value?.trend || []))
const statusChart = computed(() => sharePieOption(overview.value?.statusShare || [], STATUS_COLOR))
const triggerChart = computed(() => barOption(overview.value?.triggerShare || [], TRIGGER_COLOR))
const topWorkflowChart = computed(() => rankBarOption(overview.value?.topWorkflows || []))

function displayNum(value) {
  return value == null ? '—' : value
}

async function loadDashboard() {
  chartLoading.value = true
  chartError.value = ''
  try {
    const res = await getDashboardOverview({ days: days.value })
    overview.value = res.data || null
  } catch (error) {
    chartError.value = networkErrorMessage(error)
    overview.value = null
  } finally {
    chartLoading.value = false
  }
}

async function loadRecent() {
  const execs = await pageExecutions({ current: 1, size: 5 })
  recent.value = execs.data?.records || []
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    await Promise.all([loadDashboard(), loadRecent()])
    if (chartError.value) {
      loadError.value = chartError.value
    }
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
.quick {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.guide-alert {
  margin-bottom: 16px;
}
.recent-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 16px 16px 8px;
}
.recent :deep(.el-empty) {
  padding: 24px 16px 32px;
}
.recent-head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 650;
}
.recent-head .sub {
  margin: 4px 0 0;
}
</style>

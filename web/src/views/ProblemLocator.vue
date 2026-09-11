<template>
  <div>
    <PageHeader title="问题定位" desc="从失败执行一路点到触发来源、工作流和出错节点报文，不用猜断在哪。">
      <el-select v-model="workflowId" placeholder="全部工作流" clearable class="search-input" @change="reload">
        <el-option v-for="wf in workflows" :key="wf.id" :label="wf.workflowName" :value="wf.id" />
      </el-select>
      <el-select v-model="triggerType" placeholder="触发方式" clearable style="width: 140px" @change="reload">
        <el-option label="试运行" value="TRY_RUN" />
        <el-option label="调度" value="SCHEDULE" />
        <el-option label="开放调用" value="OPENAPI" />
        <el-option label="重放" value="REPLAY" />
      </el-select>
      <el-input
        v-model="keyword"
        class="search-input"
        placeholder="单号 / Trace"
        clearable
        @keyup.enter="reload"
        @clear="reload"
      />
      <el-button @click="reload">查询</el-button>
    </PageHeader>

    <PageState :error="loadError" @retry="boot" />

    <div class="stat-grid cols-3">
      <button type="button" class="stat-card" @click="reload">
        <div class="stat-label">待处理失败 / 超时</div>
        <div class="stat-num">{{ displayNum(total) }}</div>
      </button>
      <button type="button" class="stat-card" @click="$router.push('/executions')">
        <div class="stat-label">全部运行记录</div>
        <div class="stat-num">→</div>
        <div class="stat-hint">含成功与运行中</div>
      </button>
      <button type="button" class="stat-card stat-ok" @click="$router.push('/')">
        <div class="stat-label">工作台分析</div>
        <div class="stat-num">→</div>
        <div class="stat-hint">失败率、耗时、调度健康</div>
      </button>
    </div>

    <div class="qz-panel">
      <el-table
        class="qz-table"
        :data="records"
        v-loading="loading"
        stripe
        highlight-current-row
        @row-click="openChain"
      >
        <el-table-column label="单号" min-width="200">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="openChain(row)">{{ row.executionNo }}</el-button>
            <div class="sub">{{ formatTime(row.startTime) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="工作流" min-width="160">
          <template #default="{ row }">
            {{ row.workflowName || row.workflowId }}
            <div class="sub">{{ row.workflowCode }} · v{{ row.workflowVersion }}</div>
          </template>
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
        <el-table-column label="失败摘要" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.errorMsg" class="err">{{ row.errorMsg }}</span>
            <span v-else class="muted">无错误摘要，打开链路查看节点报文</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <div class="qz-ops" @click.stop>
              <el-button type="primary" link @click="openChain(row)">查看链路</el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty v-if="!loading && !loadError" :description="emptyText">
            <el-button type="primary" @click="$router.push('/executions')">去运行记录</el-button>
          </el-empty>
        </template>
      </el-table>
      <div class="pager">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :total="total"
          v-model:current-page="current"
          v-model:page-size="size"
          @current-change="load"
        />
      </div>
    </div>

    <ExecutionChainDrawer ref="chainDrawer" :replay-loading="replaying" @replay="onReplay" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import PageState from '@/components/PageState.vue'
import StatusTag from '@/components/StatusTag.vue'
import ExecutionChainDrawer from '@/components/ExecutionChainDrawer.vue'
import { askConfirm } from '@/utils/confirm'
import { pageWorkflows } from '@/api/workflow'
import { pageExecutions, replayExecution } from '@/api/execution'
import { networkErrorMessage } from '@/api/http'
import { execStatusLabel, formatTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const records = ref([])
const workflows = ref([])
const total = ref(0)
const current = ref(1)
const size = ref(10)
const loading = ref(false)
const loadError = ref('')
const keyword = ref('')
const workflowId = ref(null)
const triggerType = ref('')
const replaying = ref(false)
const chainDrawer = ref(null)

const emptyText = computed(() => {
  if (keyword.value || workflowId.value || triggerType.value) return '没有匹配的失败执行'
  return '最近没有失败或超时，链路是通的'
})

function displayNum(value) {
  return loading.value && !records.value.length ? '—' : value
}

function applyQuery() {
  keyword.value = route.query.keyword || ''
  workflowId.value = route.query.workflowId ? Number(route.query.workflowId) : null
  triggerType.value = route.query.triggerType || ''
  current.value = route.query.page ? Number(route.query.page) : 1
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await pageExecutions({
      current: current.value,
      size: size.value,
      keyword: keyword.value || undefined,
      workflowId: workflowId.value || undefined,
      triggerType: triggerType.value || undefined,
      problem: true,
    })
    records.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
  } catch (error) {
    loadError.value = networkErrorMessage(error)
    records.value = []
  } finally {
    loading.value = false
  }
}

function reload() {
  current.value = 1
  load()
}

function openChain(row) {
  if (!row?.id) return
  chainDrawer.value?.open(row.id)
  router.replace({
    path: '/problems',
    query: {
      ...route.query,
      id: String(row.id),
    },
  })
}

async function onReplay(row) {
  if (!row?.id) return
  if (!(await askConfirm(`按原入参重放「${row.executionNo}」？将生成新的执行单。`, '重放确认'))) return
  replaying.value = true
  try {
    const res = await replayExecution(row.id)
    const next = res.data?.instance
    ElMessage.success(`重放完成 ${next?.executionNo || ''} · ${execStatusLabel(next?.status)}`)
    await load()
    if (next?.id) {
      chainDrawer.value?.open(next.id)
    }
  } finally {
    replaying.value = false
  }
}

async function boot() {
  applyQuery()
  loadError.value = ''
  try {
    const [wfRes] = await Promise.all([
      pageWorkflows({ current: 1, size: 100 }),
      load(),
    ])
    workflows.value = wfRes.data?.records || []
    const openId = route.query.id
    if (openId) {
      chainDrawer.value?.open(Number(openId))
    }
  } catch (error) {
    loadError.value = networkErrorMessage(error)
  }
}

watch(
  () => [route.query.keyword, route.query.workflowId, route.query.triggerType, route.query.page],
  () => {
    applyQuery()
    load()
  },
)

onMounted(boot)
</script>

<style scoped>
.err { color: var(--qz-danger); font-size: 12px; }
</style>

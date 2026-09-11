<template>
  <div>
    <PageHeader title="运行记录" desc="试运行、调度、开放调用和重放都会落在这里。">
      <el-select v-model="workflowId" placeholder="全部工作流" clearable class="search-input">
        <el-option
          v-for="wf in workflows"
          :key="wf.id"
          :label="wf.workflowName"
          :value="wf.id"
        />
      </el-select>
      <el-select v-model="triggerType" placeholder="触发方式" clearable style="width: 140px">
        <el-option label="试运行" value="TRY_RUN" />
        <el-option label="调度" value="SCHEDULE" />
        <el-option label="开放调用" value="OPENAPI" />
        <el-option label="重放" value="REPLAY" />
        <el-option label="手动" value="MANUAL" />
      </el-select>
      <el-select v-model="status" placeholder="状态" clearable style="width: 120px">
        <el-option label="成功" value="SUCCESS" />
        <el-option label="失败" value="FAILED" />
        <el-option label="运行中" value="RUNNING" />
      </el-select>
      <el-input
        v-model="keyword"
        class="search-input"
        placeholder="单号 / Trace"
        clearable
        @keyup.enter="reload"
        @clear="reload"
      />
    </PageHeader>
    <div class="qz-panel">
    <el-table class="qz-table" :data="records" v-loading="loading" stripe>
      <el-table-column label="单号" min-width="200">
        <template #default="{ row }">
          <el-button type="primary" link @click="onCopy(row.executionNo)">{{ row.executionNo }}</el-button>
        </template>
      </el-table-column>
      <el-table-column label="工作流" min-width="160">
        <template #default="{ row }">
          {{ row.workflowName || row.workflowId }}
          <div class="sub">{{ row.workflowCode }} · v{{ row.workflowVersion }}</div>
        </template>
      </el-table-column>
      <el-table-column label="触发" width="110">
        <template #default="{ row }">{{ triggerLabel(row.triggerType) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="execStatusType(row.status)">{{ execStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="耗时" width="90">
        <template #default="{ row }">{{ durationText(row.durationMs) }}</template>
      </el-table-column>
      <el-table-column label="失败原因" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.errorMsg" class="err">{{ row.errorMsg }}</span>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="开始时间" min-width="170">
        <template #default="{ row }">{{ formatTime(row.startTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <div class="qz-ops">
            <el-button type="primary" link @click="openDetail(row)">详情</el-button>
            <el-button type="primary" link :disabled="row.status === 'RUNNING'" :loading="row._replaying" @click="onReplay(row)">重放</el-button>
          </div>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty v-if="!loading" description="还没有执行记录">
          <el-button type="primary" @click="$router.push('/workflows')">去编排</el-button>
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
        @current-change="onPageChange"
      />
    </div>
    </div>

    <el-drawer v-model="detailVisible" title="执行详情" size="720px" destroy-on-close @closed="resetDetail">
      <div v-loading="detailLoading">
        <div v-if="detail">
          <p>
            状态：
            <el-tag size="small" :type="execStatusType(detail.instance?.status)">{{ execStatusLabel(detail.instance?.status) }}</el-tag>
            单号：
            <el-button type="primary" link @click="onCopy(detail.instance?.executionNo)">{{ detail.instance?.executionNo }}</el-button>
          </p>
          <p>
            工作流：
            <el-button
              v-if="detailWorkflowId"
              type="primary"
              link
              @click="$router.push(`/designer/${detailWorkflowId}`)"
            >
              {{ detailWorkflowName }}
            </el-button>
            <span v-else>{{ detailWorkflowName }}</span>
          </p>
          <p>触发：{{ triggerLabel(detail.instance?.triggerType) }}　快照：{{ detail.instance?.snapshotId || '草稿' }}</p>
          <p v-if="detail.instance?.traceId">
            Trace：
            <el-button type="primary" link @click="onCopy(detail.instance.traceId, '已复制 Trace')">{{ detail.instance.traceId }}</el-button>
          </p>
          <p v-if="detail.instance?.errorMsg" class="err">{{ detail.instance.errorMsg }}</p>
          <div class="io-compare">
            <div class="io-col">
              <div class="io-head">
                <span>入参</span>
                <el-button type="primary" link @click="onCopy(formatJson(detail.instance?.inputParams), '已复制入参')">复制</el-button>
              </div>
              <pre class="payload">{{ formatJson(detail.instance?.inputParams) }}</pre>
            </div>
            <div class="io-col">
              <div class="io-head">
                <span>出参</span>
                <el-button
                  v-if="detail.instance?.outputResult"
                  type="primary"
                  link
                  @click="onCopy(formatJson(detail.instance.outputResult), '已复制出参')"
                >
                  复制
                </el-button>
              </div>
              <pre class="payload">{{ detail.instance?.outputResult ? formatJson(detail.instance.outputResult) : '暂无出参' }}</pre>
            </div>
          </div>
          <el-button type="primary" :disabled="detail.instance?.status === 'RUNNING'" :loading="replaying" @click="onReplay(detail.instance)">重放此单</el-button>
          <el-timeline>
            <el-timeline-item
              v-for="item in detail.logs"
              :key="item.id"
              :timestamp="formatTime(item.startTime)"
            >
              <div class="log-title">
                {{ item.nodeName }} {{ item.requestMethod }}
                <el-tag size="small" :type="execStatusType(item.status)">{{ execStatusLabel(item.status) }}</el-tag>
              </div>
              <div class="log-sub">{{ item.requestUrl }}</div>
              <div v-if="item.errorMsg" class="err">{{ item.errorMsg }}</div>
              <div v-else>HTTP {{ item.responseStatus }}　耗时 {{ durationText(item.durationMs) }}　重试 {{ item.retryCount }}</div>
              <div v-if="item.requestBody || item.responseBody" class="io-compare">
                <div class="io-col">
                  <div class="io-head">
                    <span>请求</span>
                    <el-button
                      v-if="item.requestBody"
                      type="primary"
                      link
                      @click="onCopy(formatJson(item.requestBody), '已复制请求')"
                    >
                      复制
                    </el-button>
                  </div>
                  <pre class="payload">{{ item.requestBody ? formatJson(item.requestBody) : '—' }}</pre>
                </div>
                <div class="io-col">
                  <div class="io-head">
                    <span>响应</span>
                    <el-button
                      v-if="item.responseBody"
                      type="primary"
                      link
                      @click="onCopy(formatJson(item.responseBody), '已复制响应')"
                    >
                      复制
                    </el-button>
                  </div>
                  <pre class="payload">{{ item.responseBody ? formatJson(item.responseBody) : '—' }}</pre>
                </div>
              </div>
            </el-timeline-item>
          </el-timeline>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { askConfirm } from '@/utils/confirm'
import { getWorkflow, pageWorkflows } from '@/api/workflow'
import { getExecution, pageExecutions, replayExecution } from '@/api/execution'
import { copyText, durationText, execStatusLabel, execStatusType, formatJson, formatTime, triggerLabel } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const records = ref([])
const workflows = ref([])
const total = ref(0)
const current = ref(1)
const size = ref(10)
const loading = ref(false)
const keyword = ref(route.query.keyword || '')
const workflowId = ref(route.query.workflowId ? Number(route.query.workflowId) : null)
const triggerType = ref(route.query.triggerType || '')
const triggerAppId = ref(route.query.triggerAppId ? Number(route.query.triggerAppId) : null)
const status = ref(route.query.status || '')
const detailVisible = ref(false)
const detail = ref(null)
const detailMeta = ref(null)
const detailLoading = ref(false)
const replaying = ref(false)
const syncingQuery = ref(false)
const detailWorkflowId = computed(() => detail.value?.instance?.workflowId || detailMeta.value?.workflowId)
const detailWorkflowName = computed(() => detailMeta.value?.workflowName || `工作流 #${detailWorkflowId.value || ''}`)

function applyQuery() {
  workflowId.value = route.query.workflowId ? Number(route.query.workflowId) : null
  triggerType.value = route.query.triggerType || ''
  triggerAppId.value = route.query.triggerAppId ? Number(route.query.triggerAppId) : null
  status.value = route.query.status || ''
  keyword.value = route.query.keyword || ''
  current.value = route.query.page ? Number(route.query.page) : 1
}

function syncQuery() {
  const query = {}
  if (workflowId.value) query.workflowId = String(workflowId.value)
  if (triggerType.value) query.triggerType = triggerType.value
  if (triggerAppId.value) query.triggerAppId = String(triggerAppId.value)
  if (status.value) query.status = status.value
  if (keyword.value) query.keyword = keyword.value
  if (current.value > 1) query.page = String(current.value)
  syncingQuery.value = true
  router.replace({ path: '/executions', query }).finally(() => {
    syncingQuery.value = false
  })
}

async function load() {
  loading.value = true
  try {
    const res = await pageExecutions({
      current: current.value,
      size: size.value,
      keyword: keyword.value,
      workflowId: workflowId.value || undefined,
      triggerType: triggerType.value || undefined,
      triggerAppId: triggerAppId.value || undefined,
      status: status.value || undefined,
    })
    records.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
  } finally {
    loading.value = false
  }
}

function reload() {
  current.value = 1
  syncQuery()
  load()
}

function onPageChange() {
  syncQuery()
  load()
}

function resetDetail() {
  detail.value = null
  detailMeta.value = null
}

async function resolveWorkflowName(meta) {
  if (meta?.workflowName) {
    return meta
  }
  const id = meta?.workflowId || detail.value?.instance?.workflowId
  if (!id) {
    return meta
  }
  const cached = workflows.value.find((item) => item.id === id)
  if (cached) {
    return { ...meta, workflowId: id, workflowName: cached.workflowName }
  }
  try {
    const res = await getWorkflow(id)
    return { ...meta, workflowId: id, workflowName: res.data?.workflowName }
  } catch {
    return meta
  }
}

async function openDetail(row) {
  detailLoading.value = true
  detailVisible.value = true
  try {
    const res = await getExecution(row.id)
    detail.value = res.data
    detailMeta.value = await resolveWorkflowName(row)
  } finally {
    detailLoading.value = false
  }
}

async function onCopy(text, message = '已复制单号') {
  await copyText(text)
  ElMessage.success(message)
}

async function onReplay(row) {
  if (!row?.id) {
    return
  }
  const hint = row.errorMsg ? `\n上次失败：${row.errorMsg}` : ''
  if (!(await askConfirm(`按原入参重放「${row.executionNo}」？将生成新的执行单。${hint}`, '重放确认'))) return
  if (row._replaying !== undefined) {
    row._replaying = true
  } else {
    replaying.value = true
  }
  try {
    const res = await replayExecution(row.id)
    const nextNo = res.data?.instance?.executionNo || ''
    ElMessage.success(`重放完成 ${nextNo} · ${execStatusLabel(res.data?.instance?.status)}`)
    keyword.value = nextNo
    current.value = 1
    syncQuery()
    await load()
    if (res.data) {
      detail.value = res.data
      detailMeta.value = await resolveWorkflowName({
        workflowId: res.data.instance?.workflowId,
        workflowName: records.value.find((item) => item.id === res.data.instance?.id)?.workflowName,
      })
      detailVisible.value = true
    }
  } finally {
    if (row._replaying !== undefined) {
      row._replaying = false
    }
    replaying.value = false
  }
}

watch(
  () => [route.query.workflowId, route.query.triggerType, route.query.triggerAppId, route.query.status, route.query.keyword, route.query.page],
  () => {
    if (syncingQuery.value) {
      return
    }
    applyQuery()
    load()
  },
)

watch([workflowId, triggerType, status], () => {
  if (syncingQuery.value) {
    return
  }
  current.value = 1
  syncQuery()
  load()
})

onMounted(async () => {
  applyQuery()
  loading.value = true
  const [wfRes] = await Promise.all([
    pageWorkflows({ current: 1, size: 100 }),
    load(),
  ])
  workflows.value = wfRes.data?.records || []
})
</script>

<style scoped>
.sub { color: #94a3b8; font-size: 12px; }
.log-title { font-weight: 600; }
.log-sub { color: #64748b; font-size: 12px; word-break: break-all; }
.err { color: #dc2626; font-size: 12px; }
.input-block { margin: 12px 0 16px; }
.io-compare {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin: 10px 0 16px;
}
.io-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 600;
}
.payload {
  margin: 0;
  padding: 8px;
  max-height: 220px;
  overflow: auto;
  background: #f8fafc;
  border: 1px solid var(--qz-border, #e8eef5);
  border-radius: 6px;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}
@media (max-width: 720px) {
  .io-compare { grid-template-columns: 1fr; }
}
</style>

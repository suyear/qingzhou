<template>
  <div>
    <PageHeader title="定时调度" desc="按规则自动触发已发布工作流，支持固定间隔、每天/每周、一次性与 Cron。">
      <el-input
        v-model="keyword"
        class="search-input"
        placeholder="搜索任务名 / 规则"
        clearable
        @keyup.enter="load"
        @clear="load"
      />
      <el-button type="primary" @click="openCreate">新建任务</el-button>
    </PageHeader>

    <el-alert
      v-if="modeHint && showModeHint"
      :title="modeHint"
      type="info"
      closable
      class="mode-alert"
      @close="showModeHint = false"
    />

    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-label">全部任务</div>
        <div class="stat-num">{{ stats.total }}</div>
      </div>
      <div class="stat-card stat-card-ok">
        <div class="stat-label">运行中</div>
        <div class="stat-num">{{ stats.running }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">已停止</div>
        <div class="stat-num">{{ stats.stopped }}</div>
      </div>
    </div>

    <div class="qz-panel">
      <el-table class="qz-table" :data="records" v-loading="loading" stripe>
        <el-table-column prop="jobName" label="任务" min-width="150" />
        <el-table-column label="工作流" min-width="180">
          <template #default="{ row }">
            <div>{{ workflowTitle(row.workflowId) }}</div>
            <div class="sub">{{ workflowCode(row.workflowId) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="调度规则" min-width="200">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ scheduleTypeLabel(row.scheduleType) }}</el-tag>
            <div class="rule-text">{{ scheduleSummary(row) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '运行中' : '已停止' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="下次触发" min-width="150">
          <template #default="{ row }">{{ formatTime(row.nextFireTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <div class="qz-ops">
              <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
              <el-button v-if="row.status !== 1" type="success" link @click="onStart(row)">启动</el-button>
              <el-button v-else type="warning" link @click="onStop(row)">停止</el-button>
              <el-button type="primary" link :loading="row._triggering" @click="onTrigger(row)">立即触发</el-button>
              <el-dropdown trigger="click" @command="(cmd) => onRowMore(cmd, row)">
                <el-button type="primary" link>更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="records">运行记录</el-dropdown-item>
                    <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty
            v-if="!loading"
            :description="workflows.length ? '还没有调度任务' : '还没有已发布工作流，发布后即可配置自动触发'"
          >
            <el-button v-if="workflows.length" type="primary" @click="openCreate">新建任务</el-button>
            <el-button v-else type="primary" @click="$router.push('/workflows')">去发布工作流</el-button>
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

    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑调度任务' : '新建调度任务'"
      width="920px"
      destroy-on-close
      class="schedule-dialog"
    >
      <el-alert
        v-if="!workflows.length"
        type="warning"
        :closable="false"
        show-icon
        class="mode-alert"
        title="当前没有已发布工作流，请先发布后再创建调度。"
      >
        <el-button type="primary" link @click="dialogVisible = false; $router.push('/workflows')">去发布</el-button>
      </el-alert>
      <el-form :model="form" label-width="108px">
        <el-form-item label="工作流" required>
          <el-select
            v-model="form.workflowId"
            filterable
            placeholder="选择已发布工作流"
            style="width: 100%"
            @change="onWorkflowChange"
          >
            <el-option
              v-for="wf in workflows"
              :key="wf.id"
              :label="`${wf.workflowName} (${wf.workflowCode})`"
              :value="wf.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="任务名称">
          <el-input v-model="form.jobName" placeholder="默认使用工作流名称" />
        </el-form-item>
        <el-form-item label="调度规则" required>
          <ScheduleRuleEditor v-model="ruleForm" />
        </el-form-item>
        <el-form-item v-if="selectedWorkflow" label="触发入参">
          <ScheduleTriggerInput
            ref="triggerInputRef"
            v-model="triggerInputData"
            :input-schema="selectedWorkflow.inputSchema"
          />
        </el-form-item>
        <el-form-item v-if="!form.id" label="保存后启动">
          <el-switch v-model="form.start" active-text="立即运行" inactive-text="仅保存" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import ScheduleRuleEditor from '@/components/schedule/ScheduleRuleEditor.vue'
import ScheduleTriggerInput from '@/components/schedule/ScheduleTriggerInput.vue'
import { askConfirm } from '@/utils/confirm'
import { formatTime } from '@/utils/format'
import { normalizeTriggerInput } from '@/utils/triggerInput'
import {
  defaultScheduleForm,
  formToPayload,
  jobToForm,
  scheduleSummary,
  scheduleTypeLabel,
} from '@/utils/scheduleRule'
import { pageWorkflows } from '@/api/workflow'
import {
  createScheduleJob,
  deleteScheduleJob,
  getScheduleMode,
  pageScheduleJobs,
  startScheduleJob,
  stopScheduleJob,
  triggerScheduleJob,
  updateScheduleJob,
} from '@/api/schedule'

const route = useRoute()
const router = useRouter()
const keyword = ref('')
const records = ref([])
const total = ref(0)
const current = ref(1)
const size = ref(10)
const loading = ref(false)
const modeHint = ref('加载调度模式…')
const showModeHint = ref(true)
const allWorkflows = ref([])
const workflows = computed(() => allWorkflows.value.filter((item) => item.status === 'PUBLISHED'))
const workflowMap = computed(() => {
  const map = {}
  for (const item of allWorkflows.value) {
    map[Number(item.id)] = item
  }
  return map
})
const stats = computed(() => {
  const running = records.value.filter((item) => item.status === 1).length
  return {
    total: total.value,
    running,
    stopped: Math.max(total.value - running, 0),
  }
})
const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive({
  id: null,
  workflowId: null,
  jobName: '',
  remark: '',
  start: true,
})
const ruleForm = ref(defaultScheduleForm())
const triggerInputData = ref(null)
const triggerInputRef = ref(null)

const selectedWorkflow = computed(() => {
  if (!form.workflowId) return null
  return workflowMap.value[form.workflowId] || workflowMap.value[Number(form.workflowId)]
})

async function loadMode() {
  const res = await getScheduleMode()
  modeHint.value = res.data?.hint || ''
}

async function load() {
  loading.value = true
  try {
    const res = await pageScheduleJobs({ current: current.value, size: size.value, keyword: keyword.value })
    records.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
  } finally {
    loading.value = false
  }
}

async function loadWorkflows() {
  const res = await pageWorkflows({ current: 1, size: 200 })
  allWorkflows.value = res.data?.records || []
}

function workflowTitle(id) {
  const wf = workflowMap.value[id] || workflowMap.value[Number(id)]
  return wf?.workflowName || `工作流 #${id}`
}

function workflowCode(id) {
  const wf = workflowMap.value[id] || workflowMap.value[Number(id)]
  return wf?.workflowCode || ''
}

function resetTriggerInput(row) {
  triggerInputData.value = normalizeTriggerInput(row?.triggerInput)
}

function resetForm() {
  form.id = null
  form.workflowId = null
  form.jobName = ''
  form.remark = ''
  form.start = true
  ruleForm.value = defaultScheduleForm()
  resetTriggerInput(null)
}

function openCreate(prefillWorkflowId) {
  resetForm()
  if (prefillWorkflowId) {
    form.workflowId = Number(prefillWorkflowId)
  }
  dialogVisible.value = true
}

function openEdit(row) {
  form.id = row.id
  form.workflowId = row.workflowId
  form.jobName = row.jobName
  form.remark = row.remark || ''
  form.start = false
  ruleForm.value = jobToForm(row)
  resetTriggerInput(row)
  dialogVisible.value = true
}

function onWorkflowChange() {
  resetTriggerInput(null)
}

function buildTriggerInputPayload() {
  const fromComponent = triggerInputRef.value?.getPayload?.()
  if (fromComponent && typeof fromComponent === 'object' && Object.keys(fromComponent).length) {
    return fromComponent
  }
  return normalizeTriggerInput(triggerInputData.value) || undefined
}

async function save() {
  if (!workflows.value.length) {
    ElMessage.warning('请先发布工作流再创建调度')
    return
  }
  if (!form.workflowId) {
    ElMessage.warning('请选择工作流')
    return
  }
  const inputError = triggerInputRef.value?.validate?.()
  if (inputError) {
    ElMessage.warning(inputError)
    return
  }
  const rulePayload = formToPayload(ruleForm.value)
  if (rulePayload.scheduleType === 'INTERVAL' && !rulePayload.intervalSeconds) {
    ElMessage.warning('请填写有效的固定间隔')
    return
  }
  if (rulePayload.scheduleType === 'WEEKLY' && !rulePayload.weekDays) {
    ElMessage.warning('请至少选择一个星期')
    return
  }
  if (rulePayload.scheduleType === 'ONCE' && !rulePayload.fireAt) {
    ElMessage.warning('请选择一次性触发时间')
    return
  }
  if (rulePayload.scheduleType === 'CRON' && !rulePayload.cronExpr) {
    ElMessage.warning('请填写 Cron 表达式')
    return
  }
  saving.value = true
  try {
    const payload = {
      workflowId: form.workflowId,
      jobName: form.jobName,
      remark: form.remark,
      start: form.start,
      ...rulePayload,
      triggerInput: buildTriggerInputPayload(),
    }
    if (form.id) {
      await updateScheduleJob(form.id, payload)
    } else {
      await createScheduleJob(payload)
    }
    ElMessage.success('已保存')
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onStart(row) {
  if (!(await askConfirm(`启动任务「${row.jobName}」？`, '启动确认', { type: 'info' }))) return
  await startScheduleJob(row.id)
  ElMessage.success('已启动')
  await load()
}

async function onStop(row) {
  if (!(await askConfirm(`停止任务「${row.jobName}」？`, '停止确认'))) return
  await stopScheduleJob(row.id)
  ElMessage.success('已停止')
  await load()
}

async function onTrigger(row) {
  if (!(await askConfirm(`立即触发「${row.jobName}」一次？`, '立即触发'))) return
  row._triggering = true
  try {
    const res = await triggerScheduleJob(row.id)
    const status = res.data?.instance?.status
    ElMessage.success(status === 'SUCCESS' ? '触发成功' : `触发完成：${status || '未知'}`)
    await load()
  } finally {
    row._triggering = false
  }
}

async function onDelete(row) {
  if (!(await askConfirm(`删除任务「${row.jobName}」？`))) return
  await deleteScheduleJob(row.id)
  ElMessage.success('已删除')
  await load()
}

function onRowMore(command, row) {
  if (command === 'records') {
    router.push({ path: '/executions', query: { workflowId: row.workflowId, triggerType: 'SCHEDULE' } })
    return
  }
  if (command === 'delete') onDelete(row)
}

onMounted(async () => {
  await Promise.all([loadMode(), load(), loadWorkflows()])
  if (route.query.workflowId) {
    openCreate(route.query.workflowId)
  }
})
</script>

<style scoped>
.mode-alert { margin-bottom: 12px; }
.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}
.stat-card {
  padding: 14px 16px;
  border-radius: 12px;
  background: var(--el-fill-color-light);
}
.stat-card-ok {
  background: var(--el-color-success-light-9);
}
.stat-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.stat-num {
  margin-top: 4px;
  font-size: 24px;
  font-weight: 700;
}
.rule-text {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-regular);
}
.sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>

<style>
.schedule-dialog .el-dialog__body {
  max-height: 72vh;
  overflow-y: auto;
}
</style>

<template>
  <div>
    <PageHeader title="凭证管理" desc="企业微信 CorpSecret 加密存储。全局凭证可被所有工作流共享，独立凭证绑定指定工作流。">
      <el-input
        v-model="keyword"
        class="search-input"
        placeholder="搜索名称 / CorpId"
        clearable
        @keyup.enter="load"
        @clear="load"
      />
      <el-button type="primary" @click="openCreate">新建凭证</el-button>
    </PageHeader>
    <div class="qz-panel">
    <el-table class="qz-table" :data="records" v-loading="loading" stripe>
      <el-table-column prop="credentialName" label="名称" min-width="150" />
      <el-table-column prop="credentialType" label="类型" width="100" />
      <el-table-column label="作用域" min-width="160">
        <template #default="{ row }">
          <span v-if="row.scope === 'WORKFLOW'">{{ workflowTitle(row.workflowId) }}</span>
          <span v-else>全局</span>
        </template>
      </el-table-column>
      <el-table-column prop="corpId" label="CorpId" min-width="160" show-overflow-tooltip />
      <el-table-column prop="agentId" label="AgentId" width="110" />
      <el-table-column label="Secret" width="90">
        <template #default="{ row }">
          <el-tag :type="row.hasSecret ? 'success' : 'danger'" size="small">
            {{ row.hasSecret ? '已配置' : '未配置' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <div class="qz-ops">
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button type="primary" link :loading="row._testing" @click="onTest(row)">测连通</el-button>
            <el-button v-if="row.status !== 1" type="success" link @click="onEnable(row)">启用</el-button>
            <el-button v-else type="warning" link @click="onDisable(row)">停用</el-button>
            <el-button type="danger" link @click="onDelete(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty v-if="!loading" description="还没有凭证，配置后设计器才能调用企业微信">
          <el-button type="primary" @click="openCreate">新建凭证</el-button>
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑凭证' : '新建凭证'" width="520px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="名称" required>
          <el-input v-model="form.credentialName" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.credentialType" style="width: 100%">
            <el-option label="企业微信" value="WECOM" />
            <el-option label="自定义" value="CUSTOM" />
          </el-select>
        </el-form-item>
        <el-form-item label="作用域">
          <el-select v-model="form.scope" style="width: 100%">
            <el-option label="全局共享" value="GLOBAL" />
            <el-option label="工作流独立" value="WORKFLOW" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.scope === 'WORKFLOW'" label="绑定工作流" required>
          <el-select v-model="form.workflowId" filterable placeholder="选择工作流" style="width: 100%">
            <el-option
              v-for="wf in workflows"
              :key="wf.id"
              :label="`${wf.workflowName} (${wf.workflowCode})`"
              :value="wf.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.credentialType === 'WECOM'" label="CorpId" required>
          <el-input v-model="form.corpId" />
        </el-form-item>
        <el-form-item v-if="form.credentialType === 'WECOM'" label="AgentId">
          <el-input v-model="form.agentId" />
        </el-form-item>
        <el-form-item :label="form.id ? 'Secret（留空不改）' : 'Secret'" :required="!form.id">
          <el-input v-model="form.secret" type="password" show-password placeholder="明文只在保存时提交" />
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
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { askConfirm } from '@/utils/confirm'
import { pageWorkflows } from '@/api/workflow'
import {
  createCredential,
  deleteCredential,
  disableCredential,
  enableCredential,
  pageCredentials,
  testCredential,
  updateCredential,
} from '@/api/credential'

const keyword = ref('')
const records = ref([])
const total = ref(0)
const current = ref(1)
const size = ref(10)
const loading = ref(false)
const workflows = ref([])
const workflowMap = computed(() => {
  const map = {}
  for (const item of workflows.value) {
    map[Number(item.id)] = item
  }
  return map
})

function workflowTitle(id) {
  const wf = workflowMap.value[id] || workflowMap.value[Number(id)]
  return wf ? `${wf.workflowName} (${wf.workflowCode})` : `工作流 #${id}`
}
const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive({
  id: null,
  credentialName: '',
  credentialType: 'WECOM',
  scope: 'GLOBAL',
  workflowId: null,
  corpId: '',
  agentId: '',
  secret: '',
  remark: '',
})

async function load() {
  loading.value = true
  try {
    const res = await pageCredentials({ current: current.value, size: size.value, keyword: keyword.value })
    records.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
  } finally {
    loading.value = false
  }
}

async function loadWorkflows() {
  const res = await pageWorkflows({ current: 1, size: 100 })
  workflows.value = res.data?.records || []
}

function resetForm() {
  form.id = null
  form.credentialName = ''
  form.credentialType = 'WECOM'
  form.scope = 'GLOBAL'
  form.workflowId = null
  form.corpId = ''
  form.agentId = ''
  form.secret = ''
  form.remark = ''
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

function openEdit(row) {
  form.id = row.id
  form.credentialName = row.credentialName
  form.credentialType = row.credentialType || 'WECOM'
  form.scope = row.scope || 'GLOBAL'
  form.workflowId = row.workflowId
  form.corpId = row.corpId || ''
  form.agentId = row.agentId || ''
  form.secret = ''
  form.remark = row.remark || ''
  dialogVisible.value = true
}

async function save() {
  if (!form.credentialName) {
    ElMessage.warning('请填写名称')
    return
  }
  if (form.credentialType === 'WECOM' && !form.corpId) {
    ElMessage.warning('请填写 CorpId')
    return
  }
  if (!form.id && !form.secret) {
    ElMessage.warning('请填写 Secret')
    return
  }
  if (form.scope === 'WORKFLOW' && !form.workflowId) {
    ElMessage.warning('请选择绑定的工作流')
    return
  }
  saving.value = true
  try {
    const payload = {
      credentialName: form.credentialName,
      credentialType: form.credentialType,
      scope: form.scope,
      workflowId: form.scope === 'WORKFLOW' ? form.workflowId : null,
      corpId: form.corpId,
      agentId: form.agentId,
      secret: form.secret || undefined,
      remark: form.remark,
    }
    if (form.id) {
      await updateCredential(form.id, payload)
    } else {
      await createCredential(payload)
    }
    ElMessage.success('已保存')
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onTest(row) {
  row._testing = true
  try {
    const res = await testCredential(row.id)
    if (res.data?.success) {
      ElMessage.success(res.data.message)
    } else {
      ElMessage.warning(res.data?.message || '连通失败')
    }
  } finally {
    row._testing = false
  }
}

async function onEnable(row) {
  if (!(await askConfirm(`启用凭证「${row.credentialName}」？`, '启用确认', { type: 'info' }))) return
  await enableCredential(row.id)
  ElMessage.success('已启用')
  await load()
}

async function onDisable(row) {
  if (!(await askConfirm(`停用「${row.credentialName}」后，依赖它的工作流将无法取 Token。`, '停用确认'))) return
  await disableCredential(row.id)
  ElMessage.success('已停用')
  await load()
}

async function onDelete(row) {
  if (!(await askConfirm(`删除凭证「${row.credentialName}」？`))) return
  await deleteCredential(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(async () => {
  await Promise.all([load(), loadWorkflows()])
})
</script>


<template>
  <div v-if="instance" class="exec-log">
    <div class="exec-head">
      <div class="exec-status">
        <el-tag size="small" :type="execStatusType(instance.status)">{{ execStatusLabel(instance.status) }}</el-tag>
        <span class="exec-no">
          单号
          <el-button type="primary" link @click="copy(instance.executionNo, '已复制单号')">{{ instance.executionNo }}</el-button>
        </span>
      </div>
      <el-button
        v-if="executionLink"
        type="primary"
        link
        @click="$router.push(executionLink)"
      >
        去运行记录
      </el-button>
    </div>

    <p v-if="workflowName" class="exec-line">
      工作流：
      <el-button v-if="workflowId" type="primary" link @click="$router.push(`/designer/${workflowId}`)">
        {{ workflowName }}
      </el-button>
      <span v-else>{{ workflowName }}</span>
    </p>
    <p class="exec-line">
      触发：{{ triggerLabel(instance.triggerType) }}
      <span v-if="instance.snapshotId">　快照：{{ instance.snapshotId }}</span>
      <span v-else>　快照：草稿</span>
      <span v-if="instance.durationMs != null">　总耗时 {{ durationText(instance.durationMs) }}</span>
    </p>
    <p v-if="instance.traceId" class="exec-line">
      Trace：
      <el-button type="primary" link @click="copy(instance.traceId, '已复制 Trace')">{{ instance.traceId }}</el-button>
    </p>
    <p v-if="instance.errorMsg" class="err">{{ instance.errorMsg }}</p>

    <div class="io-compare">
      <div class="io-col">
        <div class="payload-head">
          <span>入参</span>
          <el-button type="primary" link @click="copy(formatJson(instance.inputParams), '已复制入参')">复制</el-button>
        </div>
        <pre class="payload">{{ formatJson(instance.inputParams) }}</pre>
      </div>
      <div class="io-col">
        <div class="payload-head">
          <span>出参</span>
          <el-button
            v-if="instance.outputResult"
            type="primary"
            link
            @click="copy(formatJson(instance.outputResult), '已复制出参')"
          >
            复制
          </el-button>
        </div>
        <pre class="payload">{{ instance.outputResult ? formatJson(instance.outputResult) : '暂无出参' }}</pre>
      </div>
    </div>

    <div v-if="!logs.length" class="muted">还没有节点日志</div>
    <el-timeline v-else>
      <el-timeline-item
        v-for="(item, index) in logs"
        :key="item.id || index"
        :timestamp="formatTime(item.startTime)"
        :type="timelineType(item.status)"
      >
        <div class="log-title">
          <span>{{ index + 1 }}. {{ item.nodeName || item.nodeId }}</span>
          <el-tag size="small" :type="execStatusType(item.status)">{{ execStatusLabel(item.status) }}</el-tag>
        </div>
        <div class="log-sub">
          <span>{{ item.requestMethod }} {{ item.requestUrl || '—' }}</span>
        </div>
        <div v-if="item.errorMsg" class="err">{{ item.errorMsg }}</div>
        <div v-else class="log-meta">
          HTTP {{ item.responseStatus ?? '—' }}　耗时 {{ durationText(item.durationMs) }}　重试 {{ item.retryCount || 0 }}
        </div>
        <el-collapse v-if="hasIo(item)" v-model="opened" class="io-collapse">
          <el-collapse-item :name="reqName(item, index)" title="请求 / 响应">
            <div class="io-compare">
              <div class="io-col">
                <div class="payload-head">
                  <span>请求</span>
                  <el-button
                    v-if="item.requestBody"
                    type="primary"
                    link
                    @click="copy(formatJson(item.requestBody), '已复制请求')"
                  >
                    复制
                  </el-button>
                </div>
                <pre class="payload">{{ item.requestBody ? formatJson(item.requestBody) : '—' }}</pre>
                <template v-if="item.requestHeaders">
                  <div class="payload-head">
                    <span>请求头</span>
                    <el-button type="primary" link @click="copy(formatJson(item.requestHeaders), '已复制请求头')">复制</el-button>
                  </div>
                  <pre class="payload">{{ formatJson(item.requestHeaders) }}</pre>
                </template>
              </div>
              <div class="io-col">
                <div class="payload-head">
                  <span>响应</span>
                  <el-button
                    v-if="item.responseBody"
                    type="primary"
                    link
                    @click="copy(formatJson(item.responseBody), '已复制响应')"
                  >
                    复制
                  </el-button>
                </div>
                <pre class="payload">{{ item.responseBody ? formatJson(item.responseBody) : '—' }}</pre>
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { copyText, durationText, execStatusLabel, execStatusType, formatJson, formatTime, triggerLabel } from '@/utils/format'

const props = defineProps({
  instance: { type: Object, default: null },
  logs: { type: Array, default: () => [] },
  workflowName: { type: String, default: '' },
  workflowId: { type: [Number, String], default: '' },
  executionLink: { type: String, default: '' },
})

const opened = ref([])

function reqName(item, index) {
  return `${item.id || index}-req`
}

function hasIo(item) {
  return Boolean(item.requestBody || item.responseBody || item.requestHeaders)
}

function timelineType(status) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED' || status === 'TIMEOUT') return 'danger'
  if (status === 'RUNNING') return 'warning'
  return 'info'
}

function syncOpened() {
  const next = []
  props.logs.forEach((item, index) => {
    if (item.status === 'FAILED' || item.status === 'TIMEOUT') {
      next.push(reqName(item, index))
    }
  })
  opened.value = next
}

watch(
  () => [props.instance?.id, props.logs],
  () => syncOpened(),
  { immediate: true },
)

async function copy(text, message = '已复制') {
  await copyText(text)
  ElMessage.success(message)
}
</script>

<style scoped>
.exec-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.exec-status {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.exec-no {
  font-size: 13px;
}
.exec-line {
  margin: 0 0 8px;
  font-size: 13px;
  word-break: break-all;
}
.log-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-weight: 600;
}
.log-sub,
.log-meta {
  color: var(--qz-text-muted);
  font-size: 12px;
  word-break: break-all;
  margin-top: 4px;
}
.err {
  color: #dc2626;
  font-size: 12px;
  margin: 6px 0;
  word-break: break-all;
}
.payload-block {
  margin: 12px 0 16px;
}
.io-compare {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin: 10px 0 16px;
}
.io-col {
  min-width: 0;
}
.payload-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--qz-text-muted);
}
.payload {
  margin: 0 0 8px;
  padding: 8px 10px;
  background: #f8fafc;
  border: 1px solid var(--qz-border, #e8eef5);
  border-radius: 6px;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 240px;
  overflow: auto;
}
.io-collapse {
  margin-top: 8px;
}
.muted {
  color: var(--qz-text-muted);
  font-size: 13px;
}
@media (max-width: 720px) {
  .io-compare { grid-template-columns: 1fr; }
}
</style>

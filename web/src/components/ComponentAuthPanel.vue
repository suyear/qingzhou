<template>
  <div class="auth-panel" :class="{ embedded }">
    <div class="auth-panel-head">
      <span class="auth-panel-title">鉴权（Authorization）</span>
      <span class="auth-panel-hint">参考 Postman，配置 Token、API Key 或请求头</span>
    </div>

    <el-form label-position="top" size="default">
      <el-form-item label="鉴权方式">
        <el-select v-model="model.authType" style="width: 100%">
          <el-option
            v-for="item in AUTH_TYPES"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          >
            <div class="auth-option">
              <span>{{ item.label }}</span>
              <span class="auth-option-desc">{{ item.desc }}</span>
            </div>
          </el-option>
        </el-select>
      </el-form-item>

      <template v-if="model.authType === 'bearer'">
        <el-form-item label="Token" required>
          <el-input
            v-model="model.bearerToken"
            type="password"
            show-password
            placeholder="粘贴 Bearer Token，保存后用于每次请求"
          />
        </el-form-item>
      </template>

      <template v-else-if="model.authType === 'apiKey'">
        <div class="auth-inline">
          <el-form-item label="参数名" required class="flex-1">
            <el-input v-model="model.apiKeyName" placeholder="如 X-API-Key、api_key" />
          </el-form-item>
          <el-form-item label="添加到" class="placement-item">
            <el-radio-group v-model="model.apiKeyIn">
              <el-radio-button value="header">Header</el-radio-button>
              <el-radio-button value="query">Query</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </div>
        <el-form-item label="Key 值" required>
          <el-input
            v-model="model.apiKeyValue"
            type="password"
            show-password
            placeholder="API Key 密钥"
          />
        </el-form-item>
      </template>

      <template v-else-if="model.authType === 'basic'">
        <div class="auth-inline">
          <el-form-item label="用户名" required class="flex-1">
            <el-input v-model="model.basicUsername" placeholder="Username" />
          </el-form-item>
          <el-form-item label="密码" class="flex-1">
            <el-input
              v-model="model.basicPassword"
              type="password"
              show-password
              placeholder="Password"
            />
          </el-form-item>
        </div>
      </template>

      <el-alert
        v-else-if="model.authType === 'wecom'"
        type="info"
        :closable="false"
        show-icon
        class="wecom-alert"
      >
        <template #title>
          运行时会从「凭证管理」自动获取企业微信 AccessToken。请确保地址里包含
          <code class="inline-code">access_token=${access_token}</code>。
        </template>
        <el-button size="small" type="primary" link @click="appendWecomTokenPlaceholder">
          一键插入到地址
        </el-button>
      </el-alert>
    </el-form>

    <el-collapse class="headers-collapse">
      <el-collapse-item title="自定义请求头（可选）" name="headers">
        <div class="headers-section-inner">
          <p v-if="!model.headers.length" class="headers-empty">不需要可留空。例如 Content-Type、Accept-Language。</p>
          <div v-for="(row, index) in model.headers" :key="index" class="header-row">
            <el-checkbox v-model="row.enabled" />
            <el-input v-model="row.key" placeholder="Header 名" />
            <el-input v-model="row.value" placeholder="值" />
            <el-button type="danger" link @click="removeHeader(index)">删除</el-button>
          </div>
          <el-button size="small" @click="addHeader">+ 添加请求头</el-button>
        </div>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup>
import { AUTH_TYPES, createEmptyAuthState } from '@/utils/componentAuth'

defineProps({
  embedded: { type: Boolean, default: false },
})

const emit = defineEmits(['append-wecom-token'])
const model = defineModel({ type: Object, default: () => createEmptyAuthState() })

function ensureModel() {
  if (!model.value || typeof model.value !== 'object') {
    model.value = createEmptyAuthState()
  }
  if (!Array.isArray(model.value.headers)) {
    model.value.headers = []
  }
  if (!model.value.authType) {
    model.value.authType = 'none'
  }
}

ensureModel()

function addHeader() {
  ensureModel()
  model.value.headers.push({ key: '', value: '', enabled: true })
}

function removeHeader(index) {
  model.value.headers.splice(index, 1)
}

function appendWecomTokenPlaceholder() {
  emit('append-wecom-token')
}
</script>

<style scoped>
.auth-panel {
  padding: 14px;
  border: 1px solid var(--qz-border);
  border-radius: var(--qz-radius);
  background: var(--qz-card);
  margin-bottom: 12px;
}
.auth-panel.embedded {
  padding: 0;
  border: none;
  background: transparent;
  margin-bottom: 0;
}
.auth-panel.embedded .auth-panel-head {
  display: none;
}
.auth-panel-head {
  margin-bottom: 12px;
}
.auth-panel-title {
  display: block;
  font-weight: 600;
  font-size: 14px;
}
.auth-panel-hint {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--qz-text-muted);
}
.auth-option {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
  padding: 2px 0;
}
.auth-option-desc {
  font-size: 12px;
  color: var(--qz-text-muted);
}
.auth-inline {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 12px;
  align-items: start;
}
.flex-1 { min-width: 0; }
.placement-item :deep(.el-form-item__content) {
  justify-content: flex-end;
}
.wecom-alert { margin-bottom: 0; }
.inline-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}
.headers-collapse {
  margin-top: 8px;
}
.headers-section-inner {
  padding-top: 4px;
}
.headers-empty {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--qz-text-muted);
}
.header-row {
  display: grid;
  grid-template-columns: auto 1fr 1fr auto;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}
@media (max-width: 640px) {
  .auth-inline,
  .header-row {
    grid-template-columns: 1fr;
  }
}
</style>

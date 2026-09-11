import { parseJson } from '@/utils/schema'

export const AUTH_TYPES = [
  { value: 'none', label: '无需鉴权', desc: '公开接口或网关已处理鉴权' },
  { value: 'bearer', label: 'Bearer Token', desc: '在 Authorization 头携带 Token' },
  { value: 'apiKey', label: 'API Key', desc: '自定义 Header 或 Query 参数名' },
  { value: 'basic', label: 'Basic Auth', desc: '用户名 + 密码' },
  { value: 'wecom', label: '企业微信凭证', desc: '运行时自动取 AccessToken' },
]

export function createEmptyAuthState() {
  return {
    authType: 'none',
    bearerToken: '',
    apiKeyName: 'X-API-Key',
    apiKeyValue: '',
    apiKeyIn: 'header',
    basicUsername: '',
    basicPassword: '',
    headers: [],
  }
}

export function parseAuthFromComponent(component) {
  const config = parseJson(component?.extraConfig, {})
  const auth = config.auth && typeof config.auth === 'object' ? config.auth : {}
  const headers = Array.isArray(config.headers)
    ? config.headers.map((item) => ({
      key: item.key || '',
      value: item.value || '',
      enabled: item.enabled !== false,
    }))
    : []
  return {
    authType: auth.type || (config.needAccessToken ? 'wecom' : 'none'),
    bearerToken: auth.bearerToken || '',
    apiKeyName: auth.apiKeyName || 'X-API-Key',
    apiKeyValue: auth.apiKeyValue || '',
    apiKeyIn: auth.apiKeyIn || 'header',
    basicUsername: auth.basicUsername || '',
    basicPassword: auth.basicPassword || '',
    headers,
  }
}

export function buildExtraConfig(authState) {
  const state = authState || createEmptyAuthState()
  const auth = { type: state.authType || 'none' }
  if (auth.type === 'bearer') {
    auth.bearerToken = String(state.bearerToken || '').trim()
  }
  if (auth.type === 'apiKey') {
    auth.apiKeyName = String(state.apiKeyName || '').trim()
    auth.apiKeyValue = String(state.apiKeyValue || '').trim()
    auth.apiKeyIn = state.apiKeyIn === 'query' ? 'query' : 'header'
  }
  if (auth.type === 'basic') {
    auth.basicUsername = String(state.basicUsername || '').trim()
    auth.basicPassword = String(state.basicPassword || '')
  }

  const headers = (state.headers || [])
    .filter((item) => String(item.key || '').trim())
    .map((item) => ({
      key: String(item.key).trim(),
      value: String(item.value ?? ''),
      enabled: item.enabled !== false,
    }))

  const extraConfig = { auth }
  if (auth.type === 'wecom') {
    extraConfig.needAccessToken = true
  }
  if (headers.length) {
    extraConfig.headers = headers
  }
  return extraConfig
}

export function authTypeLabel(type) {
  return AUTH_TYPES.find((item) => item.value === type)?.label || '无需鉴权'
}

export function authSummary(component) {
  const state = parseAuthFromComponent(component)
  if (state.authType === 'none') {
    const headerCount = state.headers.filter((item) => item.enabled && item.key).length
    return headerCount ? `自定义 Header ×${headerCount}` : ''
  }
  if (state.authType === 'bearer') return 'Bearer Token'
  if (state.authType === 'apiKey') {
    const place = state.apiKeyIn === 'query' ? 'Query' : 'Header'
    return `API Key · ${state.apiKeyName || 'key'} (${place})`
  }
  if (state.authType === 'basic') return `Basic · ${state.basicUsername || '用户'}`
  if (state.authType === 'wecom') return '企业微信凭证'
  return ''
}

export function validateAuthState(authState) {
  const state = authState || createEmptyAuthState()
  if (state.authType === 'bearer' && !String(state.bearerToken || '').trim()) {
    return { valid: false, message: '请填写 Bearer Token' }
  }
  if (state.authType === 'apiKey') {
    if (!String(state.apiKeyName || '').trim()) {
      return { valid: false, message: '请填写 API Key 参数名' }
    }
    if (!String(state.apiKeyValue || '').trim()) {
      return { valid: false, message: '请填写 API Key 值' }
    }
  }
  if (state.authType === 'basic' && !String(state.basicUsername || '').trim()) {
    return { valid: false, message: '请填写 Basic Auth 用户名' }
  }
  if (state.authType === 'wecom') {
    return { valid: true }
  }
  return { valid: true }
}

export function componentNeedsCredential(component) {
  const state = parseAuthFromComponent(component)
  return state.authType === 'wecom' || needsAccessTokenFromUrl(component?.urlTemplate)
}

function needsAccessTokenFromUrl(urlTemplate) {
  return String(urlTemplate || '').includes('access_token')
}

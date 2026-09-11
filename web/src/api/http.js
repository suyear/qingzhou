import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({
  timeout: 15000,
})

http.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && typeof body.code === 'number' && body.code !== 0) {
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(body)
    }
    return body
  },
  (err) => {
    if (!err.response) {
      ElMessage.error('网络异常，请确认后端服务已启动')
    } else {
      ElMessage.error(err.response?.data?.message || err.message || '请求失败')
    }
    return Promise.reject(err)
  },
)

export default http

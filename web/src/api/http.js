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
    ElMessage.error(err.response?.data?.message || err.message || '网络异常')
    return Promise.reject(err)
  },
)

export default http

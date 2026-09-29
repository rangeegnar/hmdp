import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

/** 全局单例 axios:统一处理 token 注入、Result 解包、401 跳登录 */
const request = axios.create({
  baseURL: '',
  timeout: 15000
})

// 请求拦截:自动携带登录 token(与后端 authorization 头对应)
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('hm_token')
  if (token) config.headers['authorization'] = token
  return config
})

// 响应拦截:解包后端 Result{success,code,errorMsg,data}
request.interceptors.response.use(
  (resp) => {
    const body = resp.data
    // 后端统一返回 Result
    if (body && typeof body === 'object' && 'success' in body) {
      if (body.success) return body.data ?? null
      ElMessage.error(body.errorMsg || '请求失败')
      return Promise.reject(new Error(body.errorMsg || 'REQUEST_FAIL'))
    }
    // 非 Result 结构(如直接返回的文件流)原样返回
    return body
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      // 登录失效:清 token,回登录页(并记录来源页,登录后跳回)
      localStorage.removeItem('hm_token')
      const current = router.currentRoute.value.fullPath
      ElMessage.warning('登录已过期,请重新登录')
      router.push({ path: '/login', query: { redirect: current } })
    } else {
      const msg = error.response?.data?.errorMsg || error.message || '网络异常'
      ElMessage.error(msg)
    }
    return Promise.reject(error)
  }
)

export default request
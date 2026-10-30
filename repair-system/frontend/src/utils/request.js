import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

/**
 * axios 统一封装:
 * 1. 请求自动带上 Authorization: Bearer token
 * 2. 响应统一处理:code != 200 弹错误提示
 * 3. 401 跳转登录页
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// 请求拦截器:带上token
request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = 'Bearer ' + token
  }
  return config
})

// 响应拦截器:统一处理业务错误
request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code !== 200) {
      // 4001 技能不匹配: 由派单弹窗自己弹"是否强制派单", 这里不抢先报错
      if (res.code !== 4001) {
        ElMessage.error(res.msg || '请求失败')
      }
      const err = new Error(res.msg || '请求失败')
      err.code = res.code
      return Promise.reject(err)
    }
    return res.data
  },
  error => {
    if (error.response && error.response.status === 403) {
      ElMessage.error('没有权限执行该操作')
    } else if (error.response && error.response.status === 401) {
      localStorage.clear()
      router.push('/login')
      ElMessage.error('请先登录')
    } else {
      ElMessage.error(error.message === 'Network Error' ? '网络异常,请检查后端服务' : (error.message || '请求失败'))
    }
    return Promise.reject(error)
  }
)

export default request

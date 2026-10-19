import { defineStore } from 'pinia'

/**
 * 用户状态:登录后保存 token 与用户信息到 localStorage,刷新页面不丢失
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    id: localStorage.getItem('id') || '',
    username: localStorage.getItem('username') || '',
    realName: localStorage.getItem('realName') || '',
    role: localStorage.getItem('role') || ''
  }),
  actions: {
    setLogin(data) {
      this.token = data.token
      this.id = data.id
      this.username = data.username
      this.realName = data.realName
      this.role = data.role
      localStorage.setItem('token', data.token)
      localStorage.setItem('id', data.id)
      localStorage.setItem('username', data.username)
      localStorage.setItem('realName', data.realName)
      localStorage.setItem('role', data.role)
    },
    logout() {
      this.token = ''
      this.id = ''
      this.username = ''
      this.realName = ''
      this.role = ''
      localStorage.clear()
    }
  }
})

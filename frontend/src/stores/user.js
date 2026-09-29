import { defineStore } from 'pinia'
import { apiMe } from '../api'

/**
 * 用户登录态管理。token 持久化在 localStorage(键 hm_token),
 * userInfo 为 /user/me 返回的 UserDTO。
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('hm_token') || '',
    me: null // UserDTO{id,nickName,icon}
  }),
  getters: {
    isLogin: (s) => !!s.token,
    userId: (s) => s.me?.id || null
  },
  actions: {
    setToken(token) {
      this.token = token
      localStorage.setItem('hm_token', token)
    },
    async fetchMe() {
      try {
        this.me = await apiMe()
      } catch (e) {
        this.me = null
      }
      return this.me
    },
    clear() {
      this.token = ''
      this.me = null
      localStorage.removeItem('hm_token')
    }
  }
})
import { defineStore } from 'pinia'

function decodeExp(token) {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return payload.exp ? payload.exp * 1000 : 0
  } catch {
    return 0
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('sd_token') || '',
    refreshToken: localStorage.getItem('sd_refresh') || '',
    principal: JSON.parse(localStorage.getItem('sd_principal') || 'null'),
  }),
  getters: {
    isAuthed: (s) => !!s.token,
    isClientUser: (s) => s.principal?.role === 'CLIENT_USER',
    isSiUser: (s) => s.principal && s.principal.role !== 'CLIENT_USER',
    isManager: (s) => s.principal?.role === 'MANAGER',
    tokenExpired: (s) => {
      if (!s.token) return true
      const exp = decodeExp(s.token)
      return exp > 0 && Date.now() > exp - 5000 // 5초 여유
    },
  },
  actions: {
    setSession({ accessToken, refreshToken, principal }) {
      this.token = accessToken
      if (refreshToken) this.refreshToken = refreshToken
      if (principal) this.principal = principal
      localStorage.setItem('sd_token', accessToken)
      if (refreshToken) localStorage.setItem('sd_refresh', refreshToken)
      if (principal) localStorage.setItem('sd_principal', JSON.stringify(principal))
    },
    logout() {
      this.token = ''
      this.refreshToken = ''
      this.principal = null
      localStorage.removeItem('sd_token')
      localStorage.removeItem('sd_refresh')
      localStorage.removeItem('sd_principal')
    },
  },
})

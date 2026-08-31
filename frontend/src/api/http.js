import { useAuthStore } from '../stores/auth'
import router from '../router'

const BASE = '/api'

let refreshing = null

async function tryRefresh() {
  const auth = useAuthStore()
  if (!auth.refreshToken) return false
  if (!refreshing) {
    refreshing = fetch(BASE + '/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: auth.refreshToken }),
    })
      .then(async (r) => {
        if (!r.ok) throw new Error('refresh failed')
        auth.setSession(await r.json())
        return true
      })
      .catch(() => {
        auth.logout()
        return false
      })
      .finally(() => {
        refreshing = null
      })
  }
  return refreshing
}

export async function api(path, { method = 'GET', body, params, raw, headers: extraHeaders } = {}) {
  const auth = useAuthStore()

  // 만료 임박 토큰은 요청 전에 선제 갱신 (item 25)
  if (auth.token && auth.tokenExpired && auth.refreshToken) {
    await tryRefresh()
  }

  let url = BASE + path
  if (params) {
    const q = new URLSearchParams(
      Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== ''),
    ).toString()
    if (q) url += '?' + q
  }

  const doFetch = () => {
    const headers = { ...(extraHeaders || {}) }
    if (!raw && !(body instanceof FormData)) headers['Content-Type'] = 'application/json'
    if (auth.token) headers.Authorization = 'Bearer ' + auth.token
    return fetch(url, {
      method,
      headers,
      body: body === undefined ? undefined : body instanceof FormData ? body : JSON.stringify(body),
    })
  }

  let res = await doFetch()

  // 401 이면 한 번 리프레시 후 재시도 (item 24)
  if (res.status === 401 && auth.refreshToken && path !== '/auth/refresh') {
    const ok = await tryRefresh()
    if (ok) res = await doFetch()
  }

  if (res.status === 204) return null
  if (raw) {
    if (!res.ok) throw new Error(`요청 실패 (${res.status})`)
    return res
  }

  const data = await res.json().catch(() => null)
  if (!res.ok) {
    if (res.status === 401) {
      auth.logout()
      if (router.currentRoute.value.name !== 'login') router.push({ name: 'login' })
    }
    throw new Error(data?.message || `요청 실패 (${res.status})`)
  }
  return data
}

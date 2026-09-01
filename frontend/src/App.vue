<script setup>
import { computed } from 'vue'
import { useRoute, useRouter, RouterView, RouterLink } from 'vue-router'
import { useAuthStore } from './stores/auth'
import { api } from './api/http'
import NotificationBell from './components/NotificationBell.vue'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const showChrome = computed(() => auth.isAuthed && !route.meta.public)

const nav = computed(() => {
  if (auth.isClientUser) {
    return [
      { to: '/portal', label: '홈', ico: '🏠' },
      { to: '/docs', label: '공유 지식문서', ico: '📄' },
    ]
  }
  return [
    { to: '/', label: '대시보드', ico: '📊' },
    { to: '/clients', label: '고객사 관리', ico: '🏢' },
    { to: '/docs', label: '지식문서', ico: '📚' },
    ...(auth.isManager
      ? [
          { to: '/analytics', label: '운영 분석', ico: '🔬' },
          { to: '/reports/sla', label: 'SLA 리포트', ico: '📈' },
          { to: '/audit', label: '감사 로그', ico: '🔎' },
        ]
      : []),
    { to: '/profile', label: '설정', ico: '⚙️' },
  ]
})

const roleLabel = computed(() =>
  auth.isClientUser ? '고객사 담당자' : auth.isManager ? '관리자' : 'SI 담당자',
)
const initial = computed(() => (auth.principal?.name || '?').slice(0, 1))

async function logout() {
  try {
    await api('/auth/logout', { method: 'POST' })
  } catch (_) {}
  auth.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <div v-if="showChrome" class="shell">
    <aside class="sidebar">
      <div class="sidebar__brand"><span class="dot" /><span>SmartDesk</span></div>
      <RouterLink v-for="n in nav" :key="n.to" :to="n.to" class="nav-item">
        <span class="ico">{{ n.ico }}</span
        ><span>{{ n.label }}</span>
      </RouterLink>
    </aside>

    <div class="main">
      <header class="topbar">
        <strong style="font-size: 14px">{{
          nav.find((n) => n.to === route.path)?.label || 'SmartDesk'
        }}</strong>
        <div class="topbar__user">
          <NotificationBell />
          <span>{{ auth.principal?.name }} · {{ roleLabel }}</span>
          <span class="avatar">{{ initial }}</span>
          <button class="secondary sm" @click="logout">로그아웃</button>
        </div>
      </header>
      <main class="content">
        <RouterView />
      </main>
    </div>
  </div>
  <RouterView v-else />
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api/http'
import { dateTime } from '../labels'

const router = useRouter()
const open = ref(false)
const items = ref([])
const unread = ref(0)
let timer = null

async function load() {
  try {
    const res = await api('/notifications')
    items.value = res.items
    unread.value = res.unread
  } catch (_) {
    /* 조용히 무시 */
  }
}

async function openItem(n) {
  if (!n.read) {
    try {
      await api(`/notifications/${n.id}/read`, { method: 'PATCH' })
    } catch (_) {}
  }
  open.value = false
  if (n.ticketId) router.push(`/tickets/${n.ticketId}`)
  load()
}

async function markAll() {
  try {
    await api('/notifications/read-all', { method: 'PATCH' })
  } catch (_) {}
  load()
}

onMounted(() => {
  load()
  timer = setInterval(load, 30000)
})
onUnmounted(() => clearInterval(timer))
</script>

<template>
  <div style="position: relative">
    <button
      class="ghost"
      style="font-size: 16px; padding: 4px 8px; position: relative"
      @click="open = !open"
    >
      🔔
      <span
        v-if="unread"
        style="
          position: absolute;
          top: -2px;
          right: -2px;
          background: var(--s-red-fg);
          color: #fff;
          font-size: 10px;
          min-width: 15px;
          height: 15px;
          border-radius: 999px;
          display: grid;
          place-items: center;
          padding: 0 3px;
        "
      >
        {{ unread > 9 ? '9+' : unread }}
      </span>
    </button>

    <div
      v-if="open"
      class="card"
      style="
        position: absolute;
        right: 0;
        top: 34px;
        width: 320px;
        padding: 0;
        z-index: 20;
        max-height: 380px;
        overflow: auto;
      "
    >
      <div class="row spread" style="padding: 10px 14px; border-bottom: 1px solid var(--border)">
        <strong style="font-size: 13px">알림</strong>
        <button class="ghost" style="font-size: 12px" @click="markAll">모두 읽음</button>
      </div>
      <div v-if="!items.length" class="muted" style="padding: 18px 14px; font-size: 13px">
        알림이 없습니다.
      </div>
      <button
        v-for="n in items"
        :key="n.id"
        style="
          display: block;
          width: 100%;
          text-align: left;
          background: transparent;
          color: inherit;
          border: none;
          border-bottom: 1px solid var(--border);
          padding: 10px 14px;
          border-radius: 0;
        "
        :style="{ background: n.read ? 'transparent' : 'var(--brand-soft)' }"
        @click="openItem(n)"
      >
        <div style="font-size: 13px; font-weight: 600">{{ n.title }}</div>
        <div
          class="muted"
          style="font-size: 12px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis"
        >
          {{ n.body }}
        </div>
        <div class="muted" style="font-size: 11px; margin-top: 2px">
          {{ dateTime(n.createdAt) }}
        </div>
      </button>
    </div>
  </div>
</template>

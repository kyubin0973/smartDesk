<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api/http'
import { useAuthStore } from '../stores/auth'
import { dateTime } from '../labels'

const router = useRouter()
const auth = useAuthStore()
const sessions = ref([])
const error = ref('')

const active = computed(() =>
  sessions.value.filter((s) => !s.revoked && new Date(s.expiresAt) > new Date()),
)

async function load() {
  try {
    sessions.value = await api('/auth/sessions')
  } catch (e) {
    error.value = e.message
  }
}
onMounted(load)

async function revoke(id) {
  try {
    await api(`/auth/sessions/${id}`, { method: 'DELETE' })
    await load()
  } catch (e) {
    error.value = e.message
  }
}

async function revokeAll() {
  if (!confirm('모든 기기에서 로그아웃됩니다. 계속할까요?')) return
  try {
    await api('/auth/sessions', { method: 'DELETE' })
  } catch (_) {
    /* 어차피 로그아웃 */
  }
  auth.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="card" style="max-width: 520px">
    <div class="row spread" style="margin-bottom: 12px">
      <h3 class="card__title" style="margin: 0">로그인 세션</h3>
      <button v-if="active.length > 1" class="ghost sm" @click="revokeAll">모두 로그아웃</button>
    </div>
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>발급</th>
            <th>만료</th>
            <th>상태</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in sessions" :key="s.id">
            <td class="muted">{{ dateTime(s.createdAt) }}</td>
            <td class="muted">{{ dateTime(s.expiresAt) }}</td>
            <td>
              <span
                class="badge"
                :class="!s.revoked && new Date(s.expiresAt) > new Date() ? 'green' : 'gray'"
              >
                {{ s.revoked ? '폐기됨' : new Date(s.expiresAt) > new Date() ? '활성' : '만료' }}
              </span>
            </td>
            <td>
              <button
                v-if="!s.revoked && new Date(s.expiresAt) > new Date()"
                class="ghost sm"
                @click="revoke(s.id)"
              >
                종료
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <p class="hint">
      다른 기기·브라우저 세션을 여기서 종료할 수 있습니다. (현재 세션 구분은 미지원 — "모두
      로그아웃"은 전체 종료)
    </p>
    <p v-if="error" class="error">{{ error }}</p>
  </div>
</template>

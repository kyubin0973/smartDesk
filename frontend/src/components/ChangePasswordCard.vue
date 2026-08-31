<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api/http'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const cur = ref('')
const pw1 = ref('')
const pw2 = ref('')
const error = ref('')
const busy = ref(false)

const valid = computed(() => cur.value && pw1.value.length >= 8 && pw1.value === pw2.value)

async function submit() {
  error.value = ''
  busy.value = true
  try {
    await api('/auth/change-password', {
      method: 'POST',
      body: { currentPassword: cur.value, newPassword: pw1.value },
    })
    // 서버가 세션(리프레시 토큰)을 폐기하므로 재로그인
    auth.logout()
    router.push({ name: 'login', query: { reset: '1' } })
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="card" style="max-width: 480px">
    <h3 class="card__title">비밀번호 변경</h3>
    <div class="field"><label>현재 비밀번호</label><input v-model="cur" type="password" /></div>
    <div class="field">
      <label>새 비밀번호 (8자 이상)</label><input v-model="pw1" type="password" />
    </div>
    <div class="field"><label>새 비밀번호 확인</label><input v-model="pw2" type="password" /></div>
    <p v-if="pw2 && pw1 !== pw2" class="error">비밀번호가 일치하지 않습니다.</p>
    <button :disabled="busy || !valid" @click="submit">변경</button>
    <p v-if="error" class="error">{{ error }}</p>
  </div>
</template>

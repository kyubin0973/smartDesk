<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import { api } from '../api/http'

const route = useRoute()
const router = useRouter()
const token = route.query.token || ''

const pw1 = ref('')
const pw2 = ref('')
const error = ref('')
const busy = ref(false)

const valid = computed(() => token && pw1.value.length >= 8 && pw1.value === pw2.value)

async function submit() {
  error.value = ''
  busy.value = true
  try {
    await api('/auth/reset-password', { method: 'POST', body: { token, newPassword: pw1.value } })
    router.push({ name: 'login', query: { reset: '1' } })
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="auth">
    <div class="auth__card">
      <div class="auth__brand"><span class="dot" /> 새 비밀번호 설정</div>

      <p v-if="!token" class="error">유효하지 않은 링크입니다.</p>
      <template v-else>
        <div class="field">
          <label>새 비밀번호 (8자 이상)</label>
          <input v-model="pw1" type="password" @keyup.enter="submit" />
        </div>
        <div class="field">
          <label>새 비밀번호 확인</label>
          <input v-model="pw2" type="password" @keyup.enter="submit" />
        </div>
        <p v-if="pw2 && pw1 !== pw2" class="error">비밀번호가 일치하지 않습니다.</p>
        <button style="width: 100%; margin-top: 6px" :disabled="busy || !valid" @click="submit">
          {{ busy ? '변경 중…' : '비밀번호 변경' }}
        </button>
        <p v-if="error" class="error">{{ error }}</p>
      </template>

      <p class="hint" style="margin-top: 18px">
        <RouterLink to="/login">로그인으로 돌아가기</RouterLink>
      </p>
    </div>
  </div>
</template>

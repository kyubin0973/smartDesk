<script setup>
import { ref } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import { api } from '../api/http'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const resetDone = route.query.reset === '1'

const tab = ref('si') // 'si' | 'client'
const email = ref('')
const password = ref('')
const error = ref('')
const busy = ref(false)

async function submit() {
  error.value = ''
  busy.value = true
  try {
    const path = tab.value === 'si' ? '/auth/login' : '/auth/client-login'
    const res = await api(path, {
      method: 'POST',
      body: { email: email.value, password: password.value },
    })
    auth.setSession(res)
    router.push(tab.value === 'si' ? { name: 'dashboard' } : { name: 'portal' })
  } catch (e) {
    error.value = e.message // 5회 이상 실패 시 계정 잠금 안내가 여기로 옴
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="auth">
    <div class="auth__card">
      <div class="auth__brand"><span class="dot" /> SmartDesk</div>

      <p v-if="resetDone" class="notice" style="margin-bottom: 14px">
        비밀번호가 변경되었습니다. 새 비밀번호로 로그인하세요.
      </p>

      <div class="segmented" style="width: 100%; margin-bottom: 18px">
        <button style="flex: 1" :class="{ 'is-active': tab === 'si' }" @click="tab = 'si'">
          SI 담당자
        </button>
        <button style="flex: 1" :class="{ 'is-active': tab === 'client' }" @click="tab = 'client'">
          고객사 담당자
        </button>
      </div>

      <div class="field">
        <label>이메일</label>
        <input v-model="email" type="email" placeholder="name@company.com" @keyup.enter="submit" />
      </div>
      <div class="field">
        <label>비밀번호</label>
        <input v-model="password" type="password" placeholder="비밀번호" @keyup.enter="submit" />
      </div>

      <button
        style="width: 100%; margin-top: 6px"
        :disabled="busy || !email || !password"
        @click="submit"
      >
        {{ busy ? '확인 중…' : '로그인' }}
      </button>
      <p v-if="error" class="error">{{ error }}</p>

      <p class="hint" style="margin-top: 12px; text-align: right">
        <RouterLink to="/forgot-password">비밀번호를 잊으셨나요?</RouterLink>
      </p>

      <p class="hint" style="margin-top: 10px; line-height: 1.7">
        데모 계정 · 비밀번호 <code>Passw0rd!</code><br />
        SI <code>admin@smartdesk.io</code> · 고객사 <code>user@a-corp.com</code>
      </p>
    </div>
  </div>
</template>

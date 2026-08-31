<script setup>
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '../api/http'

const tab = ref('si')
const email = ref('')
const done = ref(false)
const devLink = ref('')
const error = ref('')
const busy = ref(false)

async function submit() {
  error.value = ''
  busy.value = true
  try {
    const res = await api('/auth/forgot-password', {
      method: 'POST',
      body: { email: email.value, principalType: tab.value === 'si' ? 'USER' : 'CLIENT_USER' },
    })
    done.value = true
    if (res.devResetToken) devLink.value = `/reset-password?token=${res.devResetToken}`
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
      <div class="auth__brand"><span class="dot" /> 비밀번호 찾기</div>

      <template v-if="!done">
        <div class="segmented" style="width: 100%; margin-bottom: 18px">
          <button style="flex: 1" :class="{ 'is-active': tab === 'si' }" @click="tab = 'si'">
            SI 담당자
          </button>
          <button
            style="flex: 1"
            :class="{ 'is-active': tab === 'client' }"
            @click="tab = 'client'"
          >
            고객사 담당자
          </button>
        </div>
        <div class="field">
          <label>가입한 이메일</label>
          <input
            v-model="email"
            type="email"
            placeholder="name@company.com"
            @keyup.enter="submit"
          />
        </div>
        <button style="width: 100%; margin-top: 6px" :disabled="busy || !email" @click="submit">
          {{ busy ? '전송 중…' : '재설정 메일 보내기' }}
        </button>
        <p v-if="error" class="error">{{ error }}</p>
      </template>

      <template v-else>
        <p>등록된 계정이면 재설정 메일을 보냈습니다. 메일함을 확인하세요.</p>
        <p v-if="devLink" class="hint" style="margin-top: 12px">
          (개발 모드) 재설정 링크: <RouterLink :to="devLink">여기</RouterLink>
        </p>
      </template>

      <p class="hint" style="margin-top: 18px">
        <RouterLink to="/login">로그인으로 돌아가기</RouterLink>
      </p>
    </div>
  </div>
</template>

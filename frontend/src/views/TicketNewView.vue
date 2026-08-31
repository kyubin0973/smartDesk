<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api/http'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

const title = ref('')
const content = ref('')
const systemId = ref(null)
const systems = ref([])
const error = ref('')
const busy = ref(false)

onMounted(async () => {
  try {
    systems.value = await api(`/clients/${auth.principal.clientId}/systems`)
  } catch (e) {
    error.value = e.message
  }
})

// 자동 카테고리 제안 (클라이언트 힌트 — 최종 분류는 서버/SI가 확정)
const suggested = computed(() => {
  const text = (title.value + ' ' + content.value).toLowerCase()
  const rules = [
    ['Access', ['접속', '권한', '로그인', 'vpn', '계정', '인증']],
    ['Application', ['오류', '에러', '배치', '버그', '500', 'exception']],
    ['Storage', ['용량', '저장', '스토리지', '백업']],
    ['Hardware', ['노트북', 'pc', '모니터', '장비', '디스크']],
    ['Purchase', ['구매', '발주', '견적', '라이선스']],
  ]
  for (const [name, kws] of rules) if (kws.some((k) => text.includes(k))) return name
  return null
})

async function submit() {
  error.value = ''
  busy.value = true
  try {
    const t = await api('/tickets', {
      method: 'POST',
      body: { title: title.value, content: content.value, systemId: systemId.value || null },
    })
    router.push(`/tickets/${t.id}`)
  } catch (e) {
    error.value = e.message // 유효 계약 없음 등
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="page-head">
    <h1>새 티켓 등록</h1>
    <p>문의 또는 장애 내용을 접수합니다</p>
  </div>

  <div class="card" style="max-width: 660px">
    <div class="field">
      <label>제목</label>
      <input v-model="title" maxlength="100" placeholder="한 줄로 요약해 주세요" />
    </div>

    <div class="field">
      <label>관련 시스템</label>
      <select v-model="systemId">
        <option :value="null">선택 안 함</option>
        <option v-for="s in systems" :key="s.id" :value="s.id">{{ s.name }}</option>
      </select>
      <p v-if="!systems.length" class="hint">등록된 시스템이 없습니다. SI 담당자에게 문의하세요.</p>
    </div>

    <div class="field">
      <label>내용</label>
      <textarea
        v-model="content"
        rows="6"
        placeholder="증상, 발생 시각, 재현 방법 등을 적어 주세요"
      ></textarea>
    </div>

    <p v-if="suggested" class="row" style="margin: 6px 0 0">
      <span class="badge blue">자동 분류 제안 · {{ suggested }}</span>
      <span class="hint" style="margin: 0">최종 카테고리는 SI 담당자가 확정합니다.</span>
    </p>

    <button style="margin-top: 18px" :disabled="busy || !title || !content" @click="submit">
      {{ busy ? '등록 중…' : '등록' }}
    </button>
    <p v-if="error" class="error">{{ error }}</p>
  </div>
</template>

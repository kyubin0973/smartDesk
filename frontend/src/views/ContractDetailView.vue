<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api/http'
import { useAuthStore } from '../stores/auth'
import { contractStatus, date } from '../labels'

const route = useRoute()
const auth = useAuthStore()
const clientId = route.params.clientId

const contracts = ref([])
const systems = ref([])
const clientUsers = ref([])
const error = ref('')
const info = ref('')

const form = ref({
  startDate: '',
  endDate: '',
  slaResponseMin: 30,
  slaResolutionMin: 480,
  maintenanceScope: '',
})
const sysForm = ref({ name: '', type: '' })
const userForm = ref({ name: '', email: '', password: '' })

async function load() {
  contracts.value = await api(`/clients/${clientId}/contracts`)
  systems.value = await api(`/clients/${clientId}/systems`)
  clientUsers.value = await api(`/clients/${clientId}/users`).catch(() => [])
}
onMounted(() => load().catch((e) => (error.value = e.message)))

async function createContract() {
  error.value = ''
  try {
    await api(`/clients/${clientId}/contracts`, { method: 'POST', body: form.value })
    await load()
  } catch (e) {
    error.value = e.message
  }
}
async function addSystem() {
  try {
    await api(`/clients/${clientId}/systems`, { method: 'POST', body: sysForm.value })
    sysForm.value = { name: '', type: '' }
    await load()
  } catch (e) {
    error.value = e.message
  }
}
async function removeSystem(id) {
  await api(`/systems/${id}`, { method: 'DELETE' })
  await load()
}
async function addClientUser() {
  error.value = ''
  try {
    await api(`/clients/${clientId}/users`, { method: 'POST', body: userForm.value })
    userForm.value = { name: '', email: '', password: '' }
    await load()
  } catch (e) {
    error.value = e.message
  }
}
async function deactivateClientUser(uid) {
  if (!confirm('이 담당자의 로그인을 차단합니다. 기존 티켓·이력은 유지됩니다.')) return
  try {
    await api(`/client-users/${uid}/deactivate`, { method: 'PATCH' })
    await load()
  } catch (e) {
    error.value = e.message
  }
}
async function offboard(contractId) {
  if (!confirm('오프보딩을 실행하면 데이터 반환·파기 절차로 넘어갑니다. 계속할까요?')) return
  info.value = ''
  error.value = ''
  try {
    const r = await api(`/contracts/${contractId}/offboarding`, { method: 'POST' })
    info.value = r.message
    await load()
  } catch (e) {
    error.value = e.message // 미해결 티켓 존재 시 차단
  }
}
</script>

<template>
  <div class="page-head">
    <h1>계약 상세 · 온보딩</h1>
    <p>계약 조건과 시스템 카탈로그를 관리합니다</p>
  </div>
  <p v-if="error" class="error">{{ error }}</p>
  <p v-if="info" class="notice" style="margin-bottom: 16px">{{ info }}</p>

  <div class="grid cols-2" style="align-items: start">
    <div class="card">
      <h3 class="card__title">계약</h3>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>기간</th>
              <th>SLA(응답/처리)</th>
              <th>상태</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="c in contracts" :key="c.id">
              <td>{{ date(c.startDate) }} – {{ date(c.endDate) }}</td>
              <td>{{ c.slaResponseMin }}분 / {{ c.slaResolutionMin }}분</td>
              <td>
                <span class="badge" :class="contractStatus(c.status).tone">{{
                  contractStatus(c.status).label
                }}</span>
              </td>
              <td><button class="ghost sm" @click="offboard(c.id)">오프보딩</button></td>
            </tr>
            <tr v-if="!contracts.length">
              <td colspan="4" class="muted">계약이 없습니다.</td>
            </tr>
          </tbody>
        </table>
      </div>

      <h4 style="margin: 18px 0 10px; font-size: 13px">신규 계약</h4>
      <div class="grid cols-2">
        <div class="field" style="margin: 0">
          <label>시작일</label><input v-model="form.startDate" type="date" />
        </div>
        <div class="field" style="margin: 0">
          <label>종료일</label><input v-model="form.endDate" type="date" />
        </div>
        <div class="field" style="margin: 0">
          <label>SLA 응답(분)</label><input v-model.number="form.slaResponseMin" type="number" />
        </div>
        <div class="field" style="margin: 0">
          <label>SLA 처리(분)</label><input v-model.number="form.slaResolutionMin" type="number" />
        </div>
      </div>
      <div class="field" style="margin-top: 12px">
        <label>유지보수 범위</label>
        <textarea v-model="form.maintenanceScope" rows="2"></textarea>
      </div>
      <button class="sm" @click="createContract">계약 등록</button>
    </div>

    <div class="card">
      <h3 class="card__title">시스템 카탈로그</h3>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>시스템</th>
              <th>구분</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="s in systems" :key="s.id">
              <td style="color: var(--ink); font-weight: 500">{{ s.name }}</td>
              <td>{{ s.type || '—' }}</td>
              <td><button class="ghost sm" @click="removeSystem(s.id)">비활성화</button></td>
            </tr>
            <tr v-if="!systems.length">
              <td colspan="3" class="muted">등록된 시스템이 없습니다.</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="row" style="margin-top: 12px">
        <input v-model="sysForm.name" placeholder="시스템명" />
        <input v-model="sysForm.type" placeholder="구분" style="max-width: 120px" />
        <button class="sm" @click="addSystem">추가</button>
      </div>
    </div>

    <div class="card">
      <h3 class="card__title">
        고객사 담당자 계정 <span class="muted" style="font-weight: 400">· 온보딩</span>
      </h3>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>이름</th>
              <th>이메일</th>
              <th>상태</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="u in clientUsers" :key="u.id">
              <td style="color: var(--ink); font-weight: 500">{{ u.name }}</td>
              <td>{{ u.email }}</td>
              <td>
                <span class="badge" :class="u.active ? 'green' : 'gray'">{{
                  u.active ? '활성' : '비활성'
                }}</span>
              </td>
              <td>
                <button
                  v-if="u.active && auth.isManager"
                  class="ghost sm"
                  @click="deactivateClientUser(u.id)"
                >
                  비활성화
                </button>
              </td>
            </tr>
            <tr v-if="!clientUsers.length">
              <td colspan="4" class="muted">발급된 계정이 없습니다.</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div
        v-if="auth.isManager"
        class="grid"
        style="grid-template-columns: 1fr 1fr auto; margin-top: 12px; align-items: end"
      >
        <div><label>이름</label><input v-model="userForm.name" /></div>
        <div><label>이메일</label><input v-model="userForm.email" type="email" /></div>
        <div><label>임시 비밀번호</label><input v-model="userForm.password" /></div>
        <button class="sm" style="grid-column: 1/-1; justify-self: start" @click="addClientUser">
          계정 발급
        </button>
      </div>
      <p v-else class="hint">계정 발급은 관리자만 가능합니다.</p>
    </div>
  </div>
</template>

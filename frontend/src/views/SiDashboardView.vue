<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api/http'
import { ticketStatus, contractStatus, slaView } from '../labels'

const router = useRouter()
const clients = ref([])
const clientId = ref(null)
const data = ref(null)
const error = ref('')

async function loadClients() {
  const res = await api('/clients', { params: { size: 100 } })
  clients.value = res.content
  if (!clientId.value && clients.value.length) clientId.value = clients.value[0].id
}
async function loadDashboard() {
  if (!clientId.value) return
  error.value = ''
  try {
    data.value = await api(`/dashboard/clients/${clientId.value}`)
  } catch (e) {
    error.value = e.message
  }
}
onMounted(async () => {
  await loadClients()
  await loadDashboard()
})
watch(clientId, loadDashboard)
</script>

<template>
  <div class="page-head">
    <h1>메인 대시보드</h1>
    <p>담당 고객사의 티켓 현황과 SLA 준수율</p>
  </div>

  <div class="field" style="max-width: 300px">
    <label>담당 고객사</label>
    <select v-model="clientId">
      <option v-for="c in clients" :key="c.id" :value="c.id">{{ c.name }}</option>
    </select>
  </div>
  <p v-if="!clients.length" class="muted">담당 고객사가 배정되지 않았습니다.</p>
  <p v-if="error" class="error">{{ error }}</p>

  <template v-if="data">
    <div class="grid cols-4" style="margin: 18px 0">
      <div class="stat">
        <div class="stat__label">이번 달 티켓</div>
        <div class="stat__value">{{ data.thisMonthTickets }}</div>
      </div>
      <div class="stat">
        <div class="stat__label">승인 대기</div>
        <div class="stat__value" :class="{ neg: data.pendingApproval > 0 }">
          {{ data.pendingApproval }}
        </div>
      </div>
      <div class="stat">
        <div class="stat__label">SLA 위반</div>
        <div class="stat__value" :class="{ neg: data.slaBreached > 0 }">{{ data.slaBreached }}</div>
      </div>
      <div class="stat">
        <div class="stat__label">SLA 준수율</div>
        <div class="stat__value pos">{{ data.slaComplianceRate }}%</div>
      </div>
    </div>

    <div class="card">
      <div class="row spread" style="margin-bottom: 14px">
        <h3 class="card__title" style="margin: 0">최근 티켓</h3>
        <span class="badge" :class="contractStatus(data.contractStatus).tone">
          {{ contractStatus(data.contractStatus).label }}
        </span>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>제목</th>
              <th>상태</th>
              <th style="width: 200px">SLA</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="t in data.recentTickets"
              :key="t.id"
              class="clickable"
              @click="router.push(`/tickets/${t.id}`)"
            >
              <td style="color: var(--ink); font-weight: 500">{{ t.title }}</td>
              <td>
                <span class="badge" :class="ticketStatus(t.status).tone">{{
                  ticketStatus(t.status).label
                }}</span>
              </td>
              <td>
                <span
                  class="badge plain"
                  :class="slaView(t.slaRemainingMinutes, t.slaBreached).tone"
                >
                  {{ slaView(t.slaRemainingMinutes, t.slaBreached).text }}</span
                >
              </td>
            </tr>
            <tr v-if="!data.recentTickets.length">
              <td colspan="3" class="muted">티켓이 없습니다.</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="row" style="margin-top: 16px">
      <button class="secondary" @click="router.push('/clients')">고객사 관리</button>
      <button class="secondary" @click="router.push('/docs')">지식문서</button>
    </div>
  </template>
</template>

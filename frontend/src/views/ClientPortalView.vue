<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api/http'
import { ticketStatus, slaView } from '../labels'
import ChangePasswordCard from '../components/ChangePasswordCard.vue'
import SessionsCard from '../components/SessionsCard.vue'

const router = useRouter()
const tickets = ref([])
const error = ref('')

async function load() {
  try {
    const res = await api('/tickets', { params: { size: 100 } })
    tickets.value = res.content
  } catch (e) {
    error.value = e.message
  }
}
onMounted(load)

const count = (s) => tickets.value.filter((t) => t.status === s).length
</script>

<template>
  <div class="page-head">
    <h1>고객사 포털</h1>
    <p>문의·장애 접수 및 처리 현황</p>
  </div>

  <div class="grid cols-3" style="margin-bottom: 16px">
    <div class="stat">
      <div class="stat__label">접수</div>
      <div class="stat__value">{{ count('RECEIVED') }}</div>
    </div>
    <div class="stat">
      <div class="stat__label">처리중</div>
      <div class="stat__value">{{ count('IN_PROGRESS') }}</div>
    </div>
    <div class="stat">
      <div class="stat__label">해결</div>
      <div class="stat__value pos">{{ count('RESOLVED') }}</div>
    </div>
  </div>

  <button @click="router.push('/portal/tickets/new')">+ 새 티켓 등록</button>
  <p v-if="error" class="error">{{ error }}</p>

  <div class="card" style="margin-top: 16px">
    <h3 class="card__title">
      내 티켓 <span class="muted" style="font-weight: 400">({{ tickets.length }})</span>
    </h3>
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
            v-for="t in tickets"
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
              <span class="badge plain" :class="slaView(t.slaRemainingMinutes, t.slaBreached).tone">
                {{ slaView(t.slaRemainingMinutes, t.slaBreached).text }}</span
              >
            </td>
          </tr>
          <tr v-if="!tickets.length">
            <td colspan="3" class="muted">등록된 티켓이 없습니다.</td>
          </tr>
        </tbody>
      </table>
    </div>

    <ChangePasswordCard style="margin-top: 16px" />
    <SessionsCard style="margin-top: 16px" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api/http'
import { downloadFile } from '../api/download'
import { dateTime } from '../labels'

const router = useRouter()
const tab = ref('security') // 'security' | 'ticket'
const rows = ref([])
const meta = ref({ page: 0, totalPages: 1, totalElements: 0 })
const error = ref('')

const filters = ref({ action: '', actorEmail: '', type: '', from: '', to: '' })

const SECURITY_ACTIONS = [
  'LOGIN_SUCCESS',
  'LOGIN_FAILURE',
  'LOGOUT',
  'PASSWORD_RESET_REQUESTED',
  'PASSWORD_RESET',
  'PASSWORD_CHANGED',
  'USER_CREATED',
  'USER_DEACTIVATED',
  'CLIENT_USER_CREATED',
  'CLIENT_USER_DEACTIVATED',
  'CONTRACT_OFFBOARDED',
  'DOCUMENT_SCOPE_CHANGED',
  'DOCUMENT_VIEWED',
]
const TICKET_EVENT_TYPES = [
  'CREATED',
  'CATEGORIZED',
  'ASSIGNED',
  'STATUS_CHANGED',
  'COMMENTED',
  'SLA_BREACHED',
  'APPROVED',
  'REJECTED',
]

function toInstant(d) {
  return d ? new Date(d + 'T00:00:00Z').toISOString() : ''
}

async function load(page = 0) {
  error.value = ''
  try {
    const base = tab.value === 'security' ? '/audit' : '/audit/ticket-events'
    const params =
      tab.value === 'security'
        ? { action: filters.value.action, actorEmail: filters.value.actorEmail }
        : { type: filters.value.type }
    params.from = toInstant(filters.value.from)
    params.to = toInstant(filters.value.to)
    params.page = page
    params.size = 30
    const res = await api(base, { params })
    rows.value = res.content
    meta.value = { page: res.page, totalPages: res.totalPages, totalElements: res.totalElements }
  } catch (e) {
    error.value = e.message
  }
}

async function exportCsv() {
  error.value = ''
  try {
    const base = tab.value === 'security' ? '/audit/export' : '/audit/ticket-events/export'
    const params =
      tab.value === 'security'
        ? { action: filters.value.action, actorEmail: filters.value.actorEmail }
        : { type: filters.value.type }
    params.from = toInstant(filters.value.from)
    params.to = toInstant(filters.value.to)
    await downloadFile(base, { params })
  } catch (e) {
    error.value = e.message
  }
}

function switchTab(t) {
  tab.value = t
  filters.value = { action: '', actorEmail: '', type: '', from: '', to: '' }
  load(0)
}

onMounted(() => load(0))
</script>

<template>
  <div class="page-head">
    <h1>감사 로그</h1>
    <p>보안·관리 이벤트와 티켓 생애주기 이벤트 (관리자 전용)</p>
  </div>

  <div class="segmented" style="margin-bottom: 16px">
    <button :class="{ 'is-active': tab === 'security' }" @click="switchTab('security')">
      보안 · 관리
    </button>
    <button :class="{ 'is-active': tab === 'ticket' }" @click="switchTab('ticket')">
      티켓 이벤트
    </button>
  </div>

  <div class="card" style="margin-bottom: 16px">
    <div class="row" style="align-items: flex-end">
      <div v-if="tab === 'security'" class="field" style="margin: 0; min-width: 200px">
        <label>액션</label>
        <select v-model="filters.action">
          <option value="">전체</option>
          <option v-for="a in SECURITY_ACTIONS" :key="a" :value="a">{{ a }}</option>
        </select>
      </div>
      <div v-if="tab === 'security'" class="field" style="margin: 0">
        <label>행위자 이메일</label>
        <input v-model="filters.actorEmail" placeholder="부분 일치" />
      </div>
      <div v-if="tab === 'ticket'" class="field" style="margin: 0; min-width: 200px">
        <label>이벤트 유형</label>
        <select v-model="filters.type">
          <option value="">전체</option>
          <option v-for="t in TICKET_EVENT_TYPES" :key="t" :value="t">{{ t }}</option>
        </select>
      </div>
      <div class="field" style="margin: 0">
        <label>시작일</label><input v-model="filters.from" type="date" />
      </div>
      <div class="field" style="margin: 0">
        <label>종료일</label><input v-model="filters.to" type="date" />
      </div>
      <button class="sm" @click="load(0)">조회</button>
      <button class="secondary sm" @click="exportCsv">CSV 내보내기</button>
    </div>
  </div>

  <p v-if="error" class="error">{{ error }}</p>

  <div class="card">
    <div class="row spread" style="margin-bottom: 12px">
      <h3 class="card__title" style="margin: 0">결과 {{ meta.totalElements }}건</h3>
      <span class="row">
        <button class="secondary sm" :disabled="meta.page <= 0" @click="load(meta.page - 1)">
          이전
        </button>
        <span class="muted" style="font-size: 13px"
          >{{ meta.page + 1 }} / {{ meta.totalPages }}</span
        >
        <button
          class="secondary sm"
          :disabled="meta.page + 1 >= meta.totalPages"
          @click="load(meta.page + 1)"
        >
          다음
        </button>
      </span>
    </div>
    <div class="table-wrap">
      <table v-if="tab === 'security'">
        <thead>
          <tr>
            <th>시각</th>
            <th>행위자</th>
            <th>액션</th>
            <th>대상</th>
            <th>상세</th>
            <th>IP</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in rows" :key="r.id">
            <td class="muted" style="white-space: nowrap">{{ dateTime(r.at) }}</td>
            <td>{{ r.actorEmail || r.actorType }}</td>
            <td>
              <span class="badge plain gray">{{ r.action }}</span>
            </td>
            <td class="muted">{{ r.targetType }}{{ r.targetId ? ' #' + r.targetId : '' }}</td>
            <td>{{ r.detail }}</td>
            <td class="muted">{{ r.ip }}</td>
          </tr>
          <tr v-if="!rows.length">
            <td colspan="6" class="muted">결과가 없습니다.</td>
          </tr>
        </tbody>
      </table>
      <table v-else>
        <thead>
          <tr>
            <th>시각</th>
            <th>티켓</th>
            <th>이벤트</th>
            <th>변경</th>
            <th>행위자</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="r in rows"
            :key="r.id"
            class="clickable"
            @click="router.push(`/tickets/${r.ticketId}`)"
          >
            <td class="muted" style="white-space: nowrap">{{ dateTime(r.at) }}</td>
            <td>#{{ r.ticketId }} {{ r.ticketTitle }}</td>
            <td>
              <span class="badge plain gray">{{ r.type }}</span>
            </td>
            <td class="muted">{{ r.fromValue || '∅' }} → {{ r.toValue || '∅' }}</td>
            <td>{{ r.actor }}</td>
          </tr>
          <tr v-if="!rows.length">
            <td colspan="5" class="muted">결과가 없습니다.</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

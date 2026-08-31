<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api/http'
import { contractStatus } from '../labels'

const router = useRouter()
const clients = ref([])
const q = ref('')
const newName = ref('')
const error = ref('')
const showForm = ref(false)

async function load() {
  const res = await api('/clients', { params: { q: q.value, size: 100 } })
  clients.value = res.content
}
onMounted(() => load().catch((e) => (error.value = e.message)))

async function create() {
  if (!newName.value.trim()) return
  try {
    await api('/clients', { method: 'POST', body: { name: newName.value } })
    newName.value = ''
    showForm.value = false
    await load()
  } catch (e) {
    error.value = e.message
  }
}
</script>

<template>
  <div class="page-head">
    <h1>고객사 · 계약 관리</h1>
    <p>고객사 기본정보와 계약 상태</p>
  </div>

  <div class="row spread" style="margin-bottom: 16px">
    <input v-model="q" placeholder="고객사 검색" style="max-width: 320px" @keyup.enter="load" />
    <button @click="showForm = !showForm">+ 신규 고객사</button>
  </div>

  <div v-if="showForm" class="card" style="margin-bottom: 16px">
    <div class="row">
      <input v-model="newName" placeholder="고객사명" @keyup.enter="create" />
      <button class="sm" @click="create">등록</button>
      <button class="secondary sm" @click="showForm = false">취소</button>
    </div>
  </div>
  <p v-if="error" class="error">{{ error }}</p>

  <div class="card">
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>고객사명</th>
            <th>계약 상태</th>
            <th>담당자</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="c in clients"
            :key="c.id"
            class="clickable"
            @click="router.push(`/clients/${c.id}/contract`)"
          >
            <td style="color: var(--ink); font-weight: 600">{{ c.name }}</td>
            <td>
              <span class="badge" :class="contractStatus(c.contractStatus).tone">{{
                contractStatus(c.contractStatus).label
              }}</span>
            </td>
            <td>{{ c.assignees.join(', ') || '—' }}</td>
          </tr>
          <tr v-if="!clients.length">
            <td colspan="3" class="muted">고객사가 없습니다.</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

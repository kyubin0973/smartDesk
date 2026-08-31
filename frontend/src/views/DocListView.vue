<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api/http'
import { useAuthStore } from '../stores/auth'
import { docScope, date } from '../labels'

const router = useRouter()
const auth = useAuthStore()
const docs = ref([])
const q = ref('')
const scope = ref('')
const error = ref('')

async function load() {
  try {
    docs.value = await api('/documents', { params: { q: q.value, scope: scope.value } })
  } catch (e) {
    error.value = e.message
  }
}
function setScope(s) {
  scope.value = s
  load()
}
onMounted(load)
</script>

<template>
  <div class="page-head">
    <h1>지식문서</h1>
    <p>매뉴얼 · FAQ · 해결사례</p>
  </div>

  <div class="row spread" style="margin-bottom: 16px">
    <div class="row">
      <input v-model="q" placeholder="제목·본문 검색" style="width: 280px" @keyup.enter="load" />
      <div v-if="auth.isSiUser" class="segmented">
        <button :class="{ 'is-active': scope === '' }" @click="setScope('')">전체</button>
        <button :class="{ 'is-active': scope === 'SI_INTERNAL' }" @click="setScope('SI_INTERNAL')">
          SI 내부
        </button>
        <button
          :class="{ 'is-active': scope === 'CLIENT_SHARED' }"
          @click="setScope('CLIENT_SHARED')"
        >
          고객사 공유
        </button>
      </div>
    </div>
    <button v-if="auth.isSiUser" @click="router.push('/docs/new')">+ 신규 문서</button>
  </div>
  <p v-if="error" class="error">{{ error }}</p>

  <div class="card">
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>제목</th>
            <th>버전</th>
            <th>공개범위</th>
            <th>최종 수정</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="d in docs"
            :key="d.id"
            class="clickable"
            @click="router.push(auth.isSiUser ? `/docs/${d.id}/edit` : `/docs/${d.id}`)"
          >
            <td style="color: var(--ink); font-weight: 500">{{ d.title }}</td>
            <td class="muted">v{{ d.version }}</td>
            <td>
              <span class="badge" :class="docScope(d.scope).tone">{{
                docScope(d.scope).label
              }}</span>
            </td>
            <td class="muted">{{ date(d.updatedAt) }}</td>
          </tr>
          <tr v-if="!docs.length">
            <td colspan="4" class="muted">검색 결과가 없습니다.</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

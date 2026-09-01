<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api/http'
import AttachmentSection from '../components/AttachmentSection.vue'
import RichTextEditor from '../components/RichTextEditor.vue'

const route = useRoute()
const router = useRouter()
const docId = route.params.documentId

const form = ref({
  title: '',
  content: '',
  scope: 'SI_INTERNAL',
  categoryId: null,
  clientIds: [],
  expectedVersion: null,
})
const categories = ref([])
const clients = ref([])
const error = ref('')

onMounted(async () => {
  categories.value = await api('/categories')
  clients.value = (await api('/clients', { params: { size: 100 } })).content
  if (docId) {
    const d = await api(`/documents/${docId}`)
    form.value = {
      title: d.title,
      content: d.content,
      scope: d.scope,
      categoryId: d.categoryId,
      clientIds: d.sharedClientIds || [],
      expectedVersion: d.version,
    }
  }
})

async function save() {
  error.value = ''
  try {
    if (docId) await api(`/documents/${docId}`, { method: 'PUT', body: form.value })
    else await api('/documents', { method: 'POST', body: form.value })
    router.push('/docs')
  } catch (e) {
    error.value = e.message // 버전 충돌 등
  }
}
</script>

<template>
  <div class="page-head">
    <h1>{{ docId ? '문서 편집' : '신규 문서' }}</h1>
    <p v-if="docId">현재 v{{ form.expectedVersion }} · 저장 시 v{{ form.expectedVersion + 1 }}</p>
  </div>

  <div class="card" style="max-width: 760px">
    <div class="field"><label>제목</label><input v-model="form.title" /></div>
    <div class="field">
      <label>본문</label>
      <RichTextEditor v-model="form.content" />
    </div>

    <div class="grid cols-2">
      <div class="field" style="margin: 0">
        <label>카테고리</label>
        <select v-model="form.categoryId">
          <option :value="null">미지정</option>
          <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
      </div>
      <div class="field" style="margin: 0">
        <label>공개범위</label>
        <select v-model="form.scope">
          <option value="SI_INTERNAL">SI 내부 전용</option>
          <option value="CLIENT_SHARED">특정 고객사 공유</option>
        </select>
      </div>
    </div>

    <div v-if="form.scope === 'CLIENT_SHARED'" class="field" style="margin-top: 14px">
      <label>공유 고객사 (다중 선택)</label>
      <select v-model="form.clientIds" multiple size="4">
        <option v-for="c in clients" :key="c.id" :value="c.id">{{ c.name }}</option>
      </select>
    </div>

    <button style="margin-top: 16px" @click="save">저장</button>
    <p v-if="error" class="error">{{ error }}</p>
  </div>

  <AttachmentSection
    v-if="docId"
    owner-type="DOCUMENT"
    :owner-id="docId"
    style="max-width: 760px; margin-top: 16px"
  />
</template>

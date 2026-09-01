<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import { api } from '../api/http'
import { useAuthStore } from '../stores/auth'
import { docScope, date } from '../labels'
import AttachmentSection from '../components/AttachmentSection.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const id = route.params.documentId

const doc = ref(null)
const error = ref('')

onMounted(async () => {
  try {
    doc.value = await api(`/documents/${id}`)
  } catch (e) {
    error.value = e.message
  }
})
</script>

<template>
  <div v-if="doc">
    <div class="page-head">
      <div class="row spread">
        <h1>{{ doc.title }}</h1>
        <span class="row">
          <span class="badge" :class="docScope(doc.scope).tone">{{
            docScope(doc.scope).label
          }}</span>
          <span class="badge gray">v{{ doc.version }}</span>
        </span>
      </div>
      <p>최종 수정 {{ date(doc.updatedAt) }}</p>
    </div>
    <p v-if="error" class="error">{{ error }}</p>

    <!-- 본문 HTML 은 서버에서 허용 태그만 남기고 sanitize 됨 (HtmlSanitizer.java) -->
    <div class="card doc-content" style="max-width: 760px" v-html="doc.content" />

    <AttachmentSection
      owner-type="DOCUMENT"
      :owner-id="id"
      readonly
      style="max-width: 760px; margin-top: 16px"
    />

    <div class="row" style="margin-top: 16px">
      <button class="secondary" @click="router.back()">목록으로</button>
      <RouterLink v-if="auth.isSiUser" :to="`/docs/${id}/edit`">
        <button>편집</button>
      </RouterLink>
    </div>
  </div>
  <p v-else-if="error" class="error">{{ error }}</p>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { api } from '../api/http'
import { useAuthStore } from '../stores/auth'

const props = defineProps({
  ownerType: { type: String, required: true }, // TICKET | DOCUMENT
  ownerId: { type: [String, Number], default: null },
  readonly: { type: Boolean, default: false },
})

const auth = useAuthStore()
const items = ref([])
const error = ref('')

async function load() {
  if (!props.ownerId) {
    items.value = []
    return
  }
  try {
    items.value = await api('/attachments', {
      params: { ownerType: props.ownerType, ownerId: props.ownerId },
    })
  } catch (e) {
    error.value = e.message
  }
}
onMounted(load)
watch(() => props.ownerId, load)

async function upload(e) {
  const file = e.target.files[0]
  if (!file) return
  const fd = new FormData()
  fd.append('ownerType', props.ownerType)
  fd.append('ownerId', props.ownerId)
  fd.append('file', file)
  try {
    await api('/attachments', { method: 'POST', body: fd })
    await load()
  } catch (err) {
    error.value = err.message
  }
  e.target.value = ''
}

async function download(a) {
  const res = await api(`/attachments/${a.id}`, { raw: true })
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = a.filename
  link.click()
  URL.revokeObjectURL(url)
}

async function remove(a) {
  if (!confirm(`${a.filename} 을(를) 삭제할까요?`)) return
  try {
    await api(`/attachments/${a.id}`, { method: 'DELETE' })
    await load()
  } catch (e) {
    error.value = e.message
  }
}
</script>

<template>
  <div class="card">
    <div class="row spread" style="margin-bottom: 12px">
      <h3 class="card__title" style="margin: 0">첨부파일</h3>
      <label v-if="!readonly && ownerId" class="btn secondary sm" style="cursor: pointer">
        파일 추가<input type="file" hidden @change="upload" />
      </label>
    </div>
    <p v-if="!ownerId" class="muted" style="font-size: 13px">저장 후 첨부할 수 있습니다.</p>
    <ul v-else-if="items.length" style="list-style: none; padding: 0; margin: 0">
      <li v-for="a in items" :key="a.id" class="row spread" style="padding: 6px 0">
        <button class="ghost sm" @click="download(a)">📎 {{ a.filename }}</button>
        <span class="row" style="gap: 8px">
          <span class="muted" style="font-size: 12px">{{ Math.round(a.sizeBytes / 1024) }} KB</span>
          <button v-if="!readonly && auth.isSiUser" class="ghost sm" @click="remove(a)">
            삭제
          </button>
        </span>
      </li>
    </ul>
    <p v-else class="muted" style="font-size: 13px">첨부파일 없음</p>
    <p v-if="error" class="error">{{ error }}</p>
  </div>
</template>

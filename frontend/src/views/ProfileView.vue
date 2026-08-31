<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api/http'
import ChangePasswordCard from '../components/ChangePasswordCard.vue'

const me = ref(null)
const departments = ref([])
const error = ref('')
const info = ref('')

onMounted(async () => {
  try {
    me.value = await api('/users/me')
    departments.value = await api('/departments')
  } catch (e) {
    error.value = e.message
  }
})

async function save() {
  info.value = ''
  error.value = ''
  try {
    me.value = await api('/users/me', {
      method: 'PUT',
      body: { name: me.value.name, departmentId: me.value.departmentId },
    })
    info.value = '저장했습니다.'
  } catch (e) {
    error.value = e.message
  }
}
</script>

<template>
  <div class="page-head">
    <h1>프로필 · 부서</h1>
    <p>이름, 소속 부서, 담당 고객사</p>
  </div>

  <div v-if="me" class="card" style="max-width: 520px">
    <div class="field"><label>이름</label><input v-model="me.name" /></div>

    <div class="field">
      <label>소속 부서</label>
      <select v-model="me.departmentId">
        <option v-for="d in departments" :key="d.id" :value="d.id">{{ d.name }}</option>
      </select>
    </div>

    <div class="field">
      <label
        >담당 고객사 <span class="muted" style="font-weight: 400">· 변경은 관리자만</span></label
      >
      <div class="row">
        <span v-for="c in me.assignedClients" :key="c.id" class="badge plain blue">{{
          c.name
        }}</span>
        <span v-if="!me.assignedClients.length" class="muted">배정 없음</span>
      </div>
    </div>

    <button style="margin-top: 8px" @click="save">저장</button>
    <p v-if="info" class="notice" style="margin-top: 10px">{{ info }}</p>
    <p v-if="error" class="error">{{ error }}</p>
  </div>

  <ChangePasswordCard style="margin-top: 16px" />
</template>

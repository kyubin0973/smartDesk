<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api/http'
import { useAuthStore } from '../stores/auth'
import { ticketStatus, priority as priorityLabel, slaView, dateTime } from '../labels'

const route = useRoute()
const auth = useAuthStore()
const id = route.params.ticketId

const ticket = ref(null)
const thread = ref({ comments: [], history: [] })
const categories = ref([])
const staff = ref([])
const relatedDocs = ref([])
const related = ref({ documents: [], tickets: [], ragUsed: false })
const attachments = ref([])
const newComment = ref('')
const error = ref('')
const draft = ref(null)
const draftBusy = ref(false)
const triageResult = ref(null)
const triageBusy = ref(false)
const slaRisk = ref(null)

const STATUSES = ['RECEIVED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED']
const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']

async function load() {
  ticket.value = await api(`/tickets/${id}`)
  thread.value = await api(`/tickets/${id}/comments`)
  relatedDocs.value = await api(`/tickets/${id}/related-documents`).catch(() => [])
  related.value = await api(`/ai/tickets/${id}/related`, { method: 'POST' }).catch(() => ({
    documents: [],
    tickets: [],
    ragUsed: false,
  }))
  attachments.value = await api('/attachments', {
    params: { ownerType: 'TICKET', ownerId: id },
  }).catch(() => [])
  if (auth.isSiUser) {
    categories.value = await api('/categories')
    staff.value = await api('/users')
    slaRisk.value = await api(`/tickets/${id}/sla-risk`).catch(() => null)
  }
}

async function runTriage(apply) {
  triageBusy.value = true
  error.value = ''
  try {
    triageResult.value = await api(`/tickets/${id}/triage${apply ? '/apply' : ''}`, {
      method: 'POST',
    })
    if (apply) await load()
  } catch (e) {
    error.value = e.message
  } finally {
    triageBusy.value = false
  }
}
onMounted(() => load().catch((e) => (error.value = e.message)))

async function call(fn) {
  try {
    await fn()
    await load()
  } catch (e) {
    error.value = e.message
  }
}
const setStatus = (s) =>
  call(() => api(`/tickets/${id}/status`, { method: 'PUT', body: { status: s } }))
const setPriority = (p) =>
  call(() => api(`/tickets/${id}/priority`, { method: 'PUT', body: { priority: p } }))
const setCategory = (cid) =>
  call(() =>
    api(`/tickets/${id}/category`, {
      method: 'PUT',
      body: { categoryId: cid ? Number(cid) : null },
    }),
  )
const setAssignee = (uid) =>
  call(() =>
    api(`/tickets/${id}/assignee`, {
      method: 'PUT',
      body: { assigneeId: uid ? Number(uid) : null },
    }),
  )
const autoAssign = () => call(() => api(`/tickets/${id}/assignee`, { method: 'PUT', body: {} }))
const approve = () => call(() => api(`/tickets/${id}/approve`, { method: 'POST' }))
function reject() {
  const reason = prompt('반려 사유를 입력하세요')
  if (!reason) return
  call(() => api(`/tickets/${id}/reject`, { method: 'POST', body: { reason } }))
}

async function generateDraft() {
  draftBusy.value = true
  error.value = ''
  try {
    draft.value = await api(`/ai/tickets/${id}/answer-draft`, { method: 'POST' })
  } catch (e) {
    error.value = e.message
  } finally {
    draftBusy.value = false
  }
}

function draftToComment() {
  if (!draft.value?.draft) return
  const cites = (draft.value.citations || []).map((c) => `[${c.n}] ${c.title}`).join('\n')
  newComment.value = draft.value.draft + (cites ? `\n\n— 출처\n${cites}` : '')
  draft.value = null
}

async function addComment() {
  if (!newComment.value.trim()) return
  await api(`/tickets/${id}/comments`, { method: 'POST', body: { content: newComment.value } })
  newComment.value = ''
  await load()
}

async function uploadFile(e) {
  const file = e.target.files[0]
  if (!file) return
  const fd = new FormData()
  fd.append('ownerType', 'TICKET')
  fd.append('ownerId', id)
  fd.append('file', file)
  try {
    await api('/attachments', { method: 'POST', body: fd })
    await load()
  } catch (err) {
    error.value = err.message
  }
  e.target.value = ''
}

async function downloadFile(a) {
  const res = await api(`/attachments/${a.id}`, { raw: true })
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = a.filename
  link.click()
  URL.revokeObjectURL(url)
}

const sla = computed(() =>
  ticket.value
    ? slaView(ticket.value.slaRemainingMinutes, ticket.value.slaBreached)
    : { tone: 'gray', text: '' },
)

// 비활성 카테고리라도 현재 값은 옵션에 포함 (item 27)
const categoryOptions = computed(() => {
  const opts = [...categories.value]
  if (ticket.value?.categoryId && !opts.some((c) => c.id === ticket.value.categoryId)) {
    opts.push({
      id: ticket.value.categoryId,
      name: (ticket.value.categoryName || '현재') + ' (비활성)',
    })
  }
  return opts
})

const timeline = computed(() => {
  const h = thread.value.history.map((x) => ({ kind: 'history', at: x.createdAt, ...x }))
  const c = thread.value.comments.map((x) => ({ kind: 'comment', at: x.createdAt, ...x }))
  return [...h, ...c].sort((a, b) => new Date(a.at) - new Date(b.at))
})
</script>

<template>
  <div v-if="ticket">
    <div class="page-head">
      <div class="row spread">
        <h1>#{{ ticket.id }} · {{ ticket.title }}</h1>
        <span class="badge plain" :class="sla.tone" style="font-size: 13px"
          >SLA {{ sla.text }}</span
        >
      </div>
      <p>
        마감 {{ dateTime(ticket.slaDueAt)
        }}<span v-if="ticket.resolvedAt"> · 해결 {{ dateTime(ticket.resolvedAt) }}</span>
      </p>
    </div>
    <p v-if="error" class="error">{{ error }}</p>

    <div class="grid" style="grid-template-columns: 1.4fr 1fr">
      <div>
        <div class="card">
          <div class="row" style="margin-bottom: 12px">
            <span class="badge" :class="ticketStatus(ticket.status).tone">{{
              ticketStatus(ticket.status).label
            }}</span>
            <span class="badge" :class="priorityLabel(ticket.priority).tone">{{
              priorityLabel(ticket.priority).label
            }}</span>
            <span class="badge gray">{{ ticket.categoryName || '미분류' }}</span>
          </div>
          <p style="white-space: pre-wrap; color: var(--ink); margin: 0">{{ ticket.content }}</p>
          <p class="hint">
            요청자 {{ ticket.requesterName }} · 시스템 {{ ticket.systemName || '미지정' }} · 등록
            {{ dateTime(ticket.createdAt) }}
          </p>
        </div>

        <div class="card">
          <div class="row spread" style="margin-bottom: 12px">
            <h3 class="card__title" style="margin: 0">첨부파일</h3>
            <label class="btn secondary sm" style="cursor: pointer">
              파일 추가<input type="file" hidden @change="uploadFile" />
            </label>
          </div>
          <ul v-if="attachments.length" style="list-style: none; padding: 0; margin: 0">
            <li v-for="a in attachments" :key="a.id" class="row spread" style="padding: 6px 0">
              <button class="ghost sm" @click="downloadFile(a)">📎 {{ a.filename }}</button>
              <span class="muted" style="font-size: 12px"
                >{{ Math.round(a.sizeBytes / 1024) }} KB</span
              >
            </li>
          </ul>
          <p v-else class="muted" style="font-size: 13px">첨부파일 없음</p>
        </div>

        <div class="card">
          <h3 class="card__title">처리 이력 / 코멘트</h3>
          <ul style="list-style: none; padding: 0; margin: 0 0 14px; display: grid; gap: 12px">
            <li v-for="(e, i) in timeline" :key="i">
              <template v-if="e.kind === 'history'">
                <span class="muted" style="font-size: 12.5px">
                  {{ dateTime(e.at) }} · <b>{{ e.field }}</b> {{ e.oldValue || '∅' }} →
                  {{ e.newValue || '∅' }}
                </span>
              </template>
              <template v-else>
                <div class="row" style="gap: 8px">
                  <span class="avatar" style="width: 24px; height: 24px; font-size: 11px">{{
                    (e.authorName || '?').slice(0, 1)
                  }}</span>
                  <b style="font-size: 13px">{{ e.authorName }}</b>
                  <span class="muted" style="font-size: 12px">{{ dateTime(e.at) }}</span>
                </div>
                <p style="margin: 4px 0 0 32px; color: var(--ink-2)">{{ e.content }}</p>
              </template>
            </li>
            <li v-if="!timeline.length" class="muted">아직 이력이 없습니다.</li>
          </ul>
          <div class="row">
            <input v-model="newComment" placeholder="코멘트 입력" @keyup.enter="addComment" />
            <button class="sm" @click="addComment">등록</button>
          </div>
        </div>
      </div>

      <div>
        <div
          v-if="ticket.status === 'RESOLVED' && auth.isManager"
          class="card"
          style="border-color: var(--brand)"
        >
          <h3 class="card__title">승인 대기</h3>
          <p class="hint" style="margin-top: 0">
            담당자가 처리를 완료했습니다. 검토 후 승인하면 종료됩니다.
          </p>
          <div class="row">
            <button @click="approve">승인 (종료)</button>
            <button class="secondary" @click="reject">반려</button>
          </div>
        </div>

        <div v-if="auth.isSiUser" class="card">
          <h3 class="card__title">처리</h3>
          <div class="field">
            <label>상태</label>
            <div class="segmented" style="width: 100%; flex-wrap: wrap">
              <button
                v-for="s in STATUSES"
                :key="s"
                style="flex: 1"
                :class="{ 'is-active': ticket.status === s }"
                :disabled="s === 'CLOSED' && ticket.status === 'RESOLVED'"
                :title="
                  s === 'CLOSED' && ticket.status === 'RESOLVED'
                    ? '종료는 관리자 승인이 필요합니다'
                    : ''
                "
                @click="setStatus(s)"
              >
                {{ ticketStatus(s).label }}
              </button>
            </div>
          </div>
          <div class="field">
            <label>우선순위</label>
            <select :value="ticket.priority" @change="setPriority($event.target.value)">
              <option v-for="p in PRIORITIES" :key="p" :value="p">
                {{ priorityLabel(p).label }}
              </option>
            </select>
          </div>
          <div class="field">
            <label
              >카테고리
              <span v-if="ticket.suggestedCategoryId" class="badge blue">제안됨</span></label
            >
            <select :value="ticket.categoryId || ''" @change="setCategory($event.target.value)">
              <option value="">미지정</option>
              <option v-for="c in categoryOptions" :key="c.id" :value="c.id">{{ c.name }}</option>
            </select>
          </div>
          <div class="field">
            <label>담당자</label>
            <div class="row">
              <select
                :value="ticket.assigneeId || ''"
                style="flex: 1"
                @change="setAssignee($event.target.value)"
              >
                <option value="">미배정</option>
                <option v-for="u in staff" :key="u.id" :value="u.id">
                  {{ u.name }} ({{ u.role }})
                </option>
              </select>
              <button class="secondary sm" @click="autoAssign">자동</button>
            </div>
          </div>
        </div>

        <div v-if="auth.isSiUser" class="card">
          <div class="row spread">
            <h3 class="card__title" style="margin: 0">
              AI 트리아지 <span class="muted">(단계 3)</span>
            </h3>
            <button class="secondary sm" :disabled="triageBusy" @click="runTriage(false)">
              {{ triageBusy ? '분석 중…' : '트리아지 실행' }}
            </button>
          </div>

          <div v-if="slaRisk && slaRisk.level !== 'LOW'" style="margin-top: 8px">
            <span class="badge" :class="slaRisk.level === 'HIGH' ? 'red' : 'amber'">
              SLA 위험 {{ Math.round(slaRisk.score * 100) }}%
            </span>
            <span v-if="slaRisk.suggestReassign" class="muted" style="font-size: 12px">
              · 재배정 검토 권장</span
            >
            <ul class="muted" style="font-size: 12px; margin: 4px 0 0; padding-left: 16px">
              <li v-for="(f, i) in slaRisk.factors" :key="i">{{ f }}</li>
            </ul>
          </div>

          <div v-if="triageResult" style="margin-top: 12px">
            <div class="row" style="gap: 6px; flex-wrap: wrap">
              <span class="badge gray">카테고리: {{ triageResult.categoryName || '미정' }}</span>
              <span class="badge" :class="priorityLabel(triageResult.priority).tone">
                {{ priorityLabel(triageResult.priority).label }}
                <span style="opacity: 0.7">({{ triageResult.prioritySource }})</span>
              </span>
              <span
                class="badge"
                :class="triageResult.confidence >= 0.7 ? 'green' : 'amber'"
                :title="'신뢰도'"
              >
                신뢰도 {{ Math.round(triageResult.confidence * 100) }}%
              </span>
            </div>
            <p style="font-size: 13px; margin: 8px 0 0">
              <strong>담당자 제안:</strong>
              {{ triageResult.suggestedAssigneeName || '후보 없음' }}
              <span class="muted"> — {{ triageResult.assigneeRationale }}</span>
            </p>
            <p v-if="triageResult.escalate" class="muted" style="font-size: 12px; margin: 4px 0 0">
              ⚠ 신뢰도가 낮아 담당자 자동 배정은 보류됩니다.
            </p>
            <p v-if="triageResult.llmNote" style="font-size: 12px; margin: 6px 0 0">
              💬 {{ triageResult.llmNote }}
            </p>
            <div
              v-if="triageResult.similar && triageResult.similar.length"
              class="muted"
              style="font-size: 12px; margin: 6px 0 0"
            >
              유사 과거 티켓:
              <RouterLink
                v-for="s in triageResult.similar"
                :key="s.ticketId"
                :to="`/tickets/${s.ticketId}`"
                style="margin-right: 8px"
                >#{{ s.ticketId }} ({{ s.priority }})</RouterLink
              >
            </div>
            <button
              class="sm"
              style="margin-top: 10px"
              :disabled="triageBusy"
              @click="runTriage(true)"
            >
              결과 적용
            </button>
          </div>
        </div>

        <div class="card">
          <div class="row spread">
            <h3 class="card__title" style="margin: 0">
              유사 문서 · 티켓
              <span v-if="related.ragUsed" class="badge plain blue" style="font-size: 10px"
                >의미 검색</span
              >
            </h3>
            <button
              v-if="auth.isSiUser && related.ragUsed"
              class="secondary sm"
              :disabled="draftBusy"
              @click="generateDraft"
            >
              {{ draftBusy ? '생성 중…' : '1차 답변 초안' }}
            </button>
          </div>

          <template v-if="related.ragUsed">
            <div
              v-for="d in related.documents"
              :key="'d' + d.id"
              style="padding: 7px 0; border-bottom: 1px solid var(--border)"
            >
              <RouterLink v-if="auth.isSiUser" :to="`/docs/${d.id}/edit`">{{ d.title }}</RouterLink>
              <RouterLink v-else :to="`/docs/${d.id}`">{{ d.title }}</RouterLink>
              <p class="muted" style="font-size: 12px; margin: 3px 0 0">{{ d.snippet }}</p>
            </div>
            <div v-for="t in related.tickets" :key="'t' + t.id" style="padding: 7px 0">
              <RouterLink :to="`/tickets/${t.id}`">#{{ t.id }} {{ t.title }}</RouterLink>
              <span class="badge gray" style="font-size: 10px">종료</span>
              <p class="muted" style="font-size: 12px; margin: 3px 0 0">{{ t.snippet }}</p>
            </div>
            <p
              v-if="!related.documents.length && !related.tickets.length"
              class="muted"
              style="font-size: 13px"
            >
              유사한 문서·티켓 없음
            </p>
          </template>

          <template v-else>
            <ul v-if="relatedDocs.length" style="list-style: none; padding: 0; margin: 8px 0 0">
              <li v-for="d in relatedDocs" :key="d.id" style="padding: 6px 0">
                <RouterLink v-if="auth.isSiUser" :to="`/docs/${d.id}/edit`">{{
                  d.title
                }}</RouterLink>
                <span v-else>{{ d.title }}</span>
                <span class="muted" style="font-size: 12px"> · v{{ d.version }}</span>
              </li>
            </ul>
            <p v-else class="muted" style="font-size: 13px">동일 카테고리 문서 없음</p>
          </template>

          <div v-if="draft" class="notice" style="margin-top: 12px">
            <div class="row spread" style="margin-bottom: 6px">
              <strong style="font-size: 13px">
                답변 초안
                <span v-if="draft.llmUsed" class="muted">· {{ draft.model }}</span>
                <span v-else class="muted">· 근거 문서만 (LLM 비활성)</span>
              </strong>
              <button v-if="draft.draft" class="ghost sm" @click="draftToComment">
                코멘트에 넣기
              </button>
            </div>
            <p v-if="draft.draft" style="white-space: pre-wrap; font-size: 13px; margin: 0">
              {{ draft.draft }}
            </p>
            <ol style="font-size: 12px; margin: 8px 0 0; padding-left: 18px">
              <li v-for="c in draft.citations" :key="c.n">
                <RouterLink :to="`/docs/${c.documentId}/edit`">{{ c.title }}</RouterLink>
                <span class="muted"> — {{ c.excerpt }}</span>
              </li>
            </ol>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

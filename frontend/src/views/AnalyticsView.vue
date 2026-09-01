<script setup>
import { ref, onMounted, computed } from 'vue'
import { api } from '../api/http'

const overview = ref(null)
const stats = ref([])
const heatmap = ref([])
const throughput = ref([])
const slaRec = ref([])
const error = ref('')
const refreshing = ref(false)
const rag = ref(null)
const ragBusy = ref(false)

const DOW = ['일', '월', '화', '수', '목', '금', '토']

async function load() {
  error.value = ''
  try {
    const [o, s, h, t, r] = await Promise.all([
      api('/analytics/overview'),
      api('/analytics/resolution-stats'),
      api('/analytics/heatmap'),
      api('/analytics/assignee-throughput'),
      api('/analytics/sla-recommendation'),
    ])
    overview.value = o
    stats.value = s
    heatmap.value = h
    throughput.value = t
    slaRec.value = r
    rag.value = await api('/ai/rag/status').catch(() => null)
  } catch (e) {
    error.value = e.message
  }
}

async function reindexRag() {
  ragBusy.value = true
  try {
    await api('/ai/rag/reindex', { method: 'POST' })
    rag.value = await api('/ai/rag/status')
  } catch (e) {
    error.value = e.message
  } finally {
    ragBusy.value = false
  }
}

async function refresh() {
  refreshing.value = true
  try {
    await api('/analytics/refresh', { method: 'POST' })
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    refreshing.value = false
  }
}

const maxP90 = computed(() => Math.max(1, ...stats.value.map((r) => Number(r.p90_minutes) || 0)))
const heatMax = computed(() =>
  Math.max(1, ...heatmap.value.map((c) => Number(c.ticket_count) || 0)),
)

function heatCount(dow, hour) {
  const cell = heatmap.value.find((c) => c.created_dow === dow && c.created_hour === hour)
  return cell ? Number(cell.ticket_count) : 0
}
function heatColor(n) {
  if (!n) return 'transparent'
  const a = 0.12 + 0.78 * (n / heatMax.value)
  return `rgba(79, 70, 229, ${a.toFixed(2)})`
}
function mins(v) {
  if (v == null) return '–'
  const n = Number(v)
  return n >= 60 ? `${(n / 60).toFixed(1)}h` : `${Math.round(n)}m`
}
function pct(v) {
  return v == null ? '–' : `${(Number(v) * 100).toFixed(1)}%`
}
function breachTone(v) {
  const n = Number(v)
  return n <= 0.05 ? 'green' : n <= 0.2 ? 'amber' : 'red'
}

onMounted(load)
</script>

<template>
  <div class="page-head">
    <div class="row spread">
      <h1>운영 분석</h1>
      <button class="secondary sm" :disabled="refreshing" @click="refresh">
        {{ refreshing ? '갱신 중…' : '마트 갱신' }}
      </button>
    </div>
    <p>ticket_event 기반 처리시간 · 재오픈 · SLA 패턴 (단계 1 데이터 분석)</p>
  </div>

  <p v-if="error" class="error">{{ error }}</p>

  <div v-if="rag" class="card" style="margin-bottom: 18px">
    <div class="row spread">
      <h3 class="card__title" style="margin: 0">
        RAG 벡터 색인 <span class="muted">(단계 2)</span>
      </h3>
      <button v-if="rag.enabled" class="secondary sm" :disabled="ragBusy" @click="reindexRag">
        {{ ragBusy ? '색인 중…' : '재색인' }}
      </button>
    </div>
    <p v-if="!rag.enabled" class="hint" style="margin-top: 6px">
      비활성 — <code>smartdesk.rag.enabled=true</code> + 임베딩 서비스(analytics/service) 필요
    </p>
    <p v-else class="muted" style="font-size: 13px; margin-top: 6px">
      문서 청크 {{ rag.documentChunks }} · 티켓 청크 {{ rag.ticketChunks }} · 답변 초안
      {{ rag.draftEnabled ? rag.draftModel : '비활성' }}
    </p>
  </div>

  <template v-if="overview">
    <div class="grid cols-4" style="margin-bottom: 18px">
      <div class="stat">
        <div class="stat__label">해결 티켓</div>
        <div class="stat__value">{{ overview.headline.resolved_count }}</div>
      </div>
      <div class="stat">
        <div class="stat__label">처리시간 p50 / p90</div>
        <div class="stat__value">
          {{ mins(overview.headline.p50_minutes) }}
          <span class="muted" style="font-size: 14px"
            >/ {{ mins(overview.headline.p90_minutes) }}</span
          >
        </div>
      </div>
      <div class="stat">
        <div class="stat__label">SLA 위반율</div>
        <div class="stat__value">
          <span class="badge" :class="breachTone(overview.headline.sla_breach_rate)">
            {{ pct(overview.headline.sla_breach_rate) }}
          </span>
        </div>
      </div>
      <div class="stat">
        <div class="stat__label">재오픈율</div>
        <div class="stat__value">{{ pct(overview.headline.reopen_rate) }}</div>
      </div>
    </div>

    <div class="card" style="margin-bottom: 18px">
      <h3 class="card__title">우선순위별 처리시간</h3>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>우선순위</th>
              <th>건수</th>
              <th>처리시간 p50</th>
              <th>SLA 위반율</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in overview.byPriority" :key="p.priority">
              <td>{{ p.priority }}</td>
              <td>{{ p.ticket_count }}</td>
              <td>{{ mins(p.p50_minutes) }}</td>
              <td>
                <span class="badge" :class="breachTone(p.sla_breach_rate)">{{
                  pct(p.sla_breach_rate)
                }}</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="card" style="margin-bottom: 18px">
      <h3 class="card__title">카테고리별 해결시간</h3>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>카테고리</th>
              <th>건수</th>
              <th style="width: 40%">p50 / p90 (분)</th>
              <th>SLA 위반율</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in stats" :key="r.category_id ?? 'none'">
              <td>{{ r.category_name }}</td>
              <td>{{ r.resolved_count }}</td>
              <td>
                <div class="bar-track">
                  <div
                    class="bar-fill p90"
                    :style="{ width: (100 * (Number(r.p90_minutes) || 0)) / maxP90 + '%' }"
                  />
                  <div
                    class="bar-fill p50"
                    :style="{ width: (100 * (Number(r.p50_minutes) || 0)) / maxP90 + '%' }"
                  />
                </div>
                <span class="muted" style="font-size: 12px"
                  >{{ mins(r.p50_minutes) }} / {{ mins(r.p90_minutes) }}</span
                >
              </td>
              <td>
                <span class="badge" :class="breachTone(r.sla_breach_rate)">{{
                  pct(r.sla_breach_rate)
                }}</span>
              </td>
            </tr>
            <tr v-if="!stats.length">
              <td colspan="4" class="muted">집계된 해결 티켓이 없습니다. (마트 갱신 필요)</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="card" style="margin-bottom: 18px">
      <h3 class="card__title">
        SLA 소요시간 권장값 <span class="muted">· 카테고리별 p90 기준</span>
      </h3>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>카테고리</th>
              <th>표본 수</th>
              <th>권장 SLA (분)</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in slaRec" :key="r.category_id ?? 'none'">
              <td>{{ r.category_name }}</td>
              <td>{{ r.sample_size }}</td>
              <td>
                <strong>{{ r.recommended_sla_minutes }}</strong>
                <span class="muted"> ({{ mins(r.recommended_sla_minutes) }})</span>
              </td>
            </tr>
            <tr v-if="!slaRec.length">
              <td colspan="3" class="muted">표본 부족</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="hint">
        표본이 쌓이면 <code>contract.sla_resolution_min</code> 을 이 값으로 조정하는 근거가 됩니다.
      </p>
    </div>

    <div class="card" style="margin-bottom: 18px">
      <h3 class="card__title">요청 요일 × 시간대</h3>
      <div class="heatmap">
        <div class="heatmap__row heatmap__head">
          <span class="heatmap__corner" />
          <span v-for="h in 24" :key="h" class="heatmap__hcol">{{ h - 1 }}</span>
        </div>
        <div v-for="d in 7" :key="d" class="heatmap__row">
          <span class="heatmap__dcol">{{ DOW[d - 1] }}</span>
          <span
            v-for="h in 24"
            :key="h"
            class="heatmap__cell"
            :style="{ background: heatColor(heatCount(d - 1, h - 1)) }"
            :title="`${DOW[d - 1]} ${h - 1}시 · ${heatCount(d - 1, h - 1)}건`"
          />
        </div>
      </div>
    </div>

    <div class="card">
      <h3 class="card__title">담당자별 처리량</h3>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>담당자</th>
              <th>해결</th>
              <th>진행중 부하</th>
              <th>처리시간 p50</th>
              <th>SLA 위반율</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="a in throughput" :key="a.assignee_id">
              <td>{{ a.name }}</td>
              <td>{{ a.resolved_count }}</td>
              <td>{{ a.open_load }}</td>
              <td>{{ mins(a.p50_minutes) }}</td>
              <td>
                <span class="badge" :class="breachTone(a.sla_breach_rate)">{{
                  pct(a.sla_breach_rate)
                }}</span>
              </td>
            </tr>
            <tr v-if="!throughput.length">
              <td colspan="5" class="muted">데이터 없음</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </template>
</template>

<style scoped>
.bar-track {
  position: relative;
  height: 14px;
  background: var(--surface-2);
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 3px;
}
.bar-fill {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  border-radius: 4px;
}
.bar-fill.p90 {
  background: var(--brand-soft);
}
.bar-fill.p50 {
  background: var(--brand);
}
.heatmap {
  overflow-x: auto;
  font-size: 11px;
}
.heatmap__row {
  display: flex;
  align-items: center;
}
.heatmap__cell {
  width: 16px;
  height: 16px;
  flex: 0 0 16px;
  border: 1px solid var(--border);
  margin: 1px;
  border-radius: 3px;
}
.heatmap__hcol {
  width: 16px;
  flex: 0 0 16px;
  text-align: center;
  margin: 1px;
  color: var(--muted);
}
.heatmap__dcol,
.heatmap__corner {
  width: 28px;
  flex: 0 0 28px;
  color: var(--muted);
  font-weight: 600;
}
</style>

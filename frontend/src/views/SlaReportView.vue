<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api/http'
import { downloadFile } from '../api/download'
import { date } from '../labels'

const report = ref(null)
const error = ref('')
const range = ref({ from: '', to: '' })

function toInstant(d, end = false) {
  if (!d) return ''
  return new Date(d + (end ? 'T23:59:59Z' : 'T00:00:00Z')).toISOString()
}

async function load() {
  error.value = ''
  try {
    report.value = await api('/reports/sla', {
      params: { from: toInstant(range.value.from), to: toInstant(range.value.to, true) },
    })
  } catch (e) {
    error.value = e.message
  }
}

async function exportCsv() {
  try {
    await downloadFile('/reports/sla/export', {
      params: { from: toInstant(range.value.from), to: toInstant(range.value.to, true) },
    })
  } catch (e) {
    error.value = e.message
  }
}

function tone(rate) {
  return rate >= 95 ? 'green' : rate >= 80 ? 'amber' : 'red'
}

onMounted(load)
</script>

<template>
  <div class="page-head">
    <h1>SLA 준수율 리포트</h1>
    <p>종결(해결·종료) 티켓 중 마감시각 내 처리 비율 (관리자 전용)</p>
  </div>

  <div class="card" style="margin-bottom: 16px">
    <div class="row" style="align-items: flex-end">
      <div class="field" style="margin: 0">
        <label>시작일</label><input v-model="range.from" type="date" />
      </div>
      <div class="field" style="margin: 0">
        <label>종료일</label><input v-model="range.to" type="date" />
      </div>
      <button class="sm" @click="load">조회</button>
      <button class="secondary sm" @click="exportCsv">CSV 내보내기</button>
    </div>
    <p class="hint" style="margin-top: 8px">비우면 최근 90일 기준입니다.</p>
  </div>

  <p v-if="error" class="error">{{ error }}</p>

  <template v-if="report">
    <div class="grid cols-4" style="margin-bottom: 16px">
      <div class="stat">
        <div class="stat__label">종결 건수</div>
        <div class="stat__value">{{ report.total }}</div>
      </div>
      <div class="stat">
        <div class="stat__label">SLA 준수</div>
        <div class="stat__value">{{ report.met }}</div>
      </div>
      <div class="stat">
        <div class="stat__label">SLA 위반</div>
        <div class="stat__value" :class="{ neg: report.breached > 0 }">{{ report.breached }}</div>
      </div>
      <div class="stat">
        <div class="stat__label">준수율</div>
        <div class="stat__value">
          <span class="badge" :class="tone(report.complianceRate)"
            >{{ report.complianceRate }}%</span
          >
        </div>
      </div>
    </div>
    <p class="muted" style="font-size: 13px; margin-bottom: 16px">
      {{ date(report.from) }} ~ {{ date(report.to) }}
    </p>

    <div class="card" style="margin-bottom: 16px">
      <h3 class="card__title">고객사별</h3>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>고객사</th>
              <th>종결</th>
              <th>준수</th>
              <th>위반</th>
              <th>준수율</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="g in report.byClient" :key="'c' + g.id">
              <td>{{ g.name }}</td>
              <td>{{ g.total }}</td>
              <td>{{ g.met }}</td>
              <td>{{ g.breached }}</td>
              <td>
                <span class="badge" :class="tone(g.complianceRate)">{{ g.complianceRate }}%</span>
              </td>
            </tr>
            <tr v-if="!report.byClient.length">
              <td colspan="5" class="muted">해당 기간 종결 티켓이 없습니다.</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="card">
      <h3 class="card__title">카테고리별</h3>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>카테고리</th>
              <th>종결</th>
              <th>준수</th>
              <th>위반</th>
              <th>준수율</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="g in report.byCategory" :key="'k' + g.id">
              <td>{{ g.name }}</td>
              <td>{{ g.total }}</td>
              <td>{{ g.met }}</td>
              <td>{{ g.breached }}</td>
              <td>
                <span class="badge" :class="tone(g.complianceRate)">{{ g.complianceRate }}%</span>
              </td>
            </tr>
            <tr v-if="!report.byCategory.length">
              <td colspan="5" class="muted">해당 기간 종결 티켓이 없습니다.</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </template>
</template>

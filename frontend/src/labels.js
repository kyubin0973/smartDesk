// 엔티티 enum ↔ 한글 라벨 / 배지 색상 매핑

export const TICKET_STATUS = {
  RECEIVED: { label: '접수', tone: 'blue' },
  IN_PROGRESS: { label: '처리중', tone: 'amber' },
  RESOLVED: { label: '해결', tone: 'green' },
  CLOSED: { label: '종료', tone: 'gray' },
}

export const CONTRACT_STATUS = {
  ACTIVE: { label: '계약중', tone: 'green' },
  EXPIRING: { label: '만료임박', tone: 'amber' },
  ENDED: { label: '종료', tone: 'gray' },
  NONE: { label: '계약없음', tone: 'gray' },
}

export const PRIORITY = {
  LOW: { label: '낮음', tone: 'gray' },
  MEDIUM: { label: '보통', tone: 'blue' },
  HIGH: { label: '높음', tone: 'amber' },
  CRITICAL: { label: '긴급', tone: 'red' },
}

export const DOC_SCOPE = {
  SI_INTERNAL: { label: 'SI 내부', tone: 'gray' },
  CLIENT_SHARED: { label: '고객사 공유', tone: 'blue' },
}

const passthrough = (v) => ({ label: v ?? '-', tone: 'gray' })

export const ticketStatus = (v) => TICKET_STATUS[v] ?? passthrough(v)
export const contractStatus = (v) => CONTRACT_STATUS[v] ?? { label: v ?? '-', tone: 'gray' }
export const priority = (v) => PRIORITY[v] ?? passthrough(v)
export const docScope = (v) => DOC_SCOPE[v] ?? passthrough(v)

/** SLA 잔여(분) → 배지 톤 + 사람이 읽는 문자열 */
export function slaView(minutes, breached) {
  if (minutes == null) return { tone: 'gray', text: 'SLA 없음' }
  if (breached || minutes < 0) return { tone: 'red', text: `${Math.abs(minutes)}분 초과` }
  if (minutes < 120) return { tone: 'amber', text: `${fmt(minutes)} 남음` }
  return { tone: 'green', text: `${fmt(minutes)} 남음` }
}

function fmt(min) {
  const h = Math.floor(min / 60)
  const m = min % 60
  return h > 0 ? `${h}시간 ${m}분` : `${m}분`
}

export const dateTime = (s) =>
  s ? new Date(s).toLocaleString('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }) : '-'
export const date = (s) => (s ? new Date(s).toLocaleDateString('ko-KR') : '-')

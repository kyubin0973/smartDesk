import { useAuthStore } from '../stores/auth'

const BASE = '/api'

/**
 * 0.5-b: 알림 실시간 스트림.
 * EventSource 는 Authorization 헤더를 실을 수 없어 fetch 스트리밍으로 SSE 프레임을 직접 파싱한다.
 * `onPoke` 는 "새 알림 있음" 신호마다 호출된다 (호출부에서 목록을 다시 불러옴).
 * @returns 스트림을 닫는 함수
 */
export function openNotificationStream(onPoke) {
  const auth = useAuthStore()
  let stopped = false
  let controller = null
  let backoff = 0

  async function connect() {
    if (stopped || !auth.token) return
    controller = new AbortController()
    try {
      const res = await fetch(BASE + '/notifications/stream', {
        headers: { Authorization: 'Bearer ' + auth.token, Accept: 'text/event-stream' },
        signal: controller.signal,
      })
      if (!res.ok || !res.body) throw new Error('stream ' + res.status)
      backoff = 0
      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      let buf = ''
      for (;;) {
        const { value, done } = await reader.read()
        if (done) break
        buf += decoder.decode(value, { stream: true })
        let sep
        while ((sep = buf.indexOf('\n\n')) >= 0) {
          const frame = buf.slice(0, sep)
          buf = buf.slice(sep + 2)
          if (/^event:\s*notification/m.test(frame)) onPoke()
        }
      }
    } catch {
      /* 연결 끊김 — 아래에서 재시도 */
    }
    if (stopped) return
    backoff = Math.min(backoff + 1, 6)
    setTimeout(connect, 1000 * 2 ** backoff) // 2s → 최대 ~2분 백오프
  }

  connect()
  return () => {
    stopped = true
    if (controller) controller.abort()
  }
}

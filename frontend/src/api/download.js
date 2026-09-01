import { api } from './http'

/** 0.5-d: 인증 헤더가 필요한 파일을 blob 으로 받아 다운로드시킨다. */
export async function downloadFile(path, { params, filename } = {}) {
  const res = await api(path, { params, raw: true })
  const blob = await res.blob()
  const name =
    filename ||
    res.headers.get('Content-Disposition')?.match(/filename="?([^"]+)"?/)?.[1] ||
    'download'
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = name
  link.click()
  URL.revokeObjectURL(url)
}

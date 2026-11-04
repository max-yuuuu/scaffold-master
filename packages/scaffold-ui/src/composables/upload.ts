import { scaffoldUi } from '../providers.ts'

/* ============================================================
   文件上传：项目不引入 axios，统一用 fetch。
   鉴权与宿主的 http 注入完全一致 —— Authorization: Bearer <http.token()>。
   后端返回统一信封 { code, message, data }；同时兼容 RuoYi 的 msg 字段。
   ============================================================ */

export type UploadQuery = Record<string, string | number | boolean | null | undefined>

export interface UploadOptions {
  /** 文件字段名，RuoYi 后端固定读 file */
  field?: string
  /** 随文件一起提交的表单字段（如 updateSupport、bizType） */
  data?: Record<string, unknown>
  /** 拼在 URL 上的查询参数 */
  query?: UploadQuery
  signal?: AbortSignal
}

/** 上传失败：带上后端信封的 code / status / traceId，由宿主的 errorMessage 翻译成人话 */
export class UploadError extends Error {
  code: number
  status: number
  traceId?: string

  constructor(message: string, code: number, status: number, traceId?: string) {
    super(message)
    this.name = 'UploadError'
    this.code = code
    this.status = status
    this.traceId = traceId
  }
}

/** 与宿主 http 相同的地址拼接规则：base + /action?query */
export function apiUrl(action: string, query?: UploadQuery) {
  const raw = scaffoldUi().http.baseUrl()
  const base = raw.endsWith('/') ? raw.slice(0, -1) : raw
  const path = action.startsWith('/') ? action : `/${action}`
  const params = new URLSearchParams()
  Object.entries(query ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') params.set(key, String(value))
  })
  const qs = params.toString()
  return `${base}${path}${qs ? `?${qs}` : ''}`
}

export function authHeaders(): Record<string, string> {
  const token = scaffoldUi().http.token()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

/**
 * 上传单个文件，成功返回信封里的 data（无信封时返回整个 JSON）。
 * 失败抛 UploadError，交给 Upload 组件统一 toast。
 */
export async function postUpload(action: string, file: File, options: UploadOptions = {}): Promise<unknown> {
  const form = new FormData()
  form.append(options.field ?? 'file', file, file.name)
  Object.entries(options.data ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null) form.append(key, String(value))
  })

  let response: Response
  try {
    response = await fetch(apiUrl(action, options.query), {
      method: 'POST',
      headers: authHeaders(),
      body: form,
      signal: options.signal,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new UploadError(error instanceof Error && error.message ? `上传失败：${error.message}` : '上传失败', 0, 0)
  }

  const text = await response.text()
  let payload: Record<string, unknown> | null = null
  if (text) {
    try {
      payload = JSON.parse(text) as Record<string, unknown>
    } catch {
      payload = null
    }
  }
  const code = Number(payload?.code ?? response.status)
  const message = String(payload?.message ?? payload?.msg ?? '')
  if (!response.ok || code !== 200) {
    throw new UploadError(message || '上传失败', code || response.status, response.status, payload?.traceId as string | undefined)
  }
  return payload && 'data' in payload ? payload.data : payload
}

import assert from 'node:assert/strict'
import { test } from 'node:test'
import { ref } from 'vue'
import { installScaffoldUi, scaffoldUi } from '../providers.ts'
import { materialApi } from '../api.ts'
import { apiUrl, authHeaders } from '../composables/upload.ts'

/* 注入点与物料接口路径的回归测试：
   包不 import 宿主，所以这两条约定只能在这里钉住 —— 宿主换 http 实现或改端点路径都会被打回来。 */

function fakeHttp() {
  const calls: Array<{ method: string; path: string; body?: unknown }> = []
  return {
    calls,
    http: {
      baseUrl: () => '/api',
      token: () => 'tk-123',
      get: async <T>(path: string, query?: Record<string, unknown>) => {
        calls.push({ method: 'GET', path: `${path}${query ? `?${new URLSearchParams(Object.entries(query).map(([k, v]) => [k, String(v)]))}` : ''}` })
        return [] as unknown as T
      },
      put: async <T>(path: string, body?: unknown) => {
        calls.push({ method: 'PUT', path, body })
        return undefined as T
      },
      errorMessage: (error: unknown, fallback: string) => (error instanceof Error ? error.message : fallback),
    },
  }
}

test('未安装就取能力：直接报错并给出安装入口', () => {
  assert.throws(() => scaffoldUi(), /installScaffoldUi/)
})

test('安装后 apiUrl 与 authHeaders 走注入的 baseUrl 与 token', () => {
  const { http } = fakeHttp()
  installScaffoldUi({
    http,
    permission: { has: () => true, pathOf: () => '/system/user', sessionVersion: ref(0) },
    dict: { load: async () => [] },
  })
  assert.equal(apiUrl('system/user/importData', { updateSupport: true, empty: '' }), '/api/system/user/importData?updateSupport=true')
  assert.deepEqual(authHeaders(), { Authorization: 'Bearer tk-123' })
})

test('物料数据入口锁定底座接口路径', async () => {
  const { http, calls } = fakeHttp()
  installScaffoldUi({
    http,
    permission: { has: () => true, pathOf: () => null, sessionVersion: ref(0) },
    dict: { load: async () => [] },
  })
  await materialApi.deptTree()
  await materialApi.dictRows('sys_normal_disable')
  await materialApi.updateDictRow(7, { status: 1 })
  await materialApi.usersPage({ postId: 3, pageNum: 1, pageSize: 50 })
  await materialApi.permissionGraph()
  await materialApi.loginHeatmap(30)
  await materialApi.configsPage({ pageNum: 1, pageSize: 100 })
  assert.deepEqual(calls.map((c) => `${c.method} ${c.path}`), [
    'GET /system/depts/tree',
    'GET /system/dict/data/sys_normal_disable',
    'PUT /system/dict/data/7',
    'GET /system/users?postId=3&pageNum=1&pageSize=50',
    'GET /system/permissions/graph',
    'GET /monitor/login-logs/heatmap?days=30',
    'GET /system/configs?pageNum=1&pageSize=100',
  ])
})

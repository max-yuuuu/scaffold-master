import test from 'node:test'
import assert from 'node:assert/strict'
import { createHttpClient } from '../http.ts'
import { createPlatformApi } from '../platform.ts'

/**
 * 页面接口客户端（PR 6C）。钉的是「前端拼出去的 URL 与请求体形状 = 后端那六个入口」。
 *
 * 后端那层的规则（设计与模型逐格对齐）在用例 PageApiTest 里，这里只保证前端不会把一个合法设计
 * 送错地方，或者把编辑请求发成新增。
 */
test('pages api maps list detail create update status and remove to backend endpoints', async () => {
  const calls: Array<{ url: string, method: string, body?: string }> = []
  const fetcher = async (url: string | URL | Request, init?: RequestInit) => {
    calls.push({ url: String(url), method: init?.method ?? 'GET', body: init?.body as string | undefined })
    return new Response(JSON.stringify({ code: 200, message: 'success', data: { id: 7, rows: [], total: 0 }, traceId: 't' }), {
      status: 200,
      headers: { 'content-type': 'application/json' },
    })
  }
  const api = createPlatformApi(createHttpClient({ baseUrl: '/api', tokenProvider: () => 'token', fetcher }))

  const design = {
    version: '1.0',
    pageType: 'list',
    modelRef: 'sales_order',
    layout: { type: 'PageContainer', props: { title: '销售订单' }, children: [] },
  }
  await api.pages.list({ pageNum: 1, pageSize: 20, projectId: 3, keyword: 'order' })
  await api.pages.detail(7)
  await api.pages.create({ projectId: 3, modelCode: 'sales_order', pageCode: 'sales_order_list', pageName: '销售订单列表', design })
  await api.pages.update(7, { pageName: '销售订单', routePath: '/trade/orders', menuIcon: 'cart', menuSort: 3, design })
  await api.pages.updateStatus(7, 0)
  await api.pages.remove(7)

  assert.equal(calls[0].url, '/api/lowcode/pages?pageNum=1&pageSize=20&projectId=3&keyword=order')
  assert.equal(calls[1].url, '/api/lowcode/pages/7')

  assert.equal(calls[2].method, 'POST')
  assert.equal(calls[2].url, '/api/lowcode/pages')
  const created = JSON.parse(calls[2].body ?? '{}')
  assert.equal(created.projectId, 3)
  assert.equal(created.modelCode, 'sales_order')
  assert.equal(created.pageCode, 'sales_order_list')
  // design 是对象而不是字符串：让前端 stringify 一遍，后端就得判「这里到底是串还是对象」
  assert.equal(typeof created.design, 'object')
  assert.deepEqual(created.design.layout.props, { title: '销售订单' })

  assert.equal(calls[3].method, 'PUT')
  assert.equal(calls[3].url, '/api/lowcode/pages/7')
  assert.deepEqual(JSON.parse(calls[3].body ?? '{}').routePath, '/trade/orders')

  assert.equal(calls[4].method, 'PUT')
  assert.equal(calls[4].url, '/api/lowcode/pages/7/status')
  assert.deepEqual(JSON.parse(calls[4].body ?? '{}'), { status: 0 })

  assert.equal(calls[5].method, 'DELETE')
  assert.equal(calls[5].url, '/api/lowcode/pages/7')
})

test('发布只送 force 与 changeDesc 两个入参，版本号由后端按 current_version 算', async () => {
  const calls: Array<{ url: string, method: string, body?: string }> = []
  const fetcher = async (url: string | URL | Request, init?: RequestInit) => {
    calls.push({ url: String(url), method: init?.method ?? 'GET', body: init?.body as string | undefined })
    return new Response(JSON.stringify({
      code: 200,
      message: 'success',
      data: { pageId: 59, version: 2, status: 1, tagName: 'vorder-serive-sales_order_page-2', message: '已发布' },
      traceId: 't',
    }), { status: 200, headers: { 'content-type': 'application/json' } })
  }
  const api = createPlatformApi(createHttpClient({ baseUrl: '/api', tokenProvider: () => 'token', fetcher }))

  const result = await api.pages.publish(59, { force: false, changeDesc: '加一行导出备注' })

  assert.equal(calls[0].method, 'POST')
  assert.equal(calls[0].url, '/api/lowcode/pages/59/publish')
  assert.deepEqual(JSON.parse(calls[0].body ?? '{}'), { force: false, changeDesc: '加一行导出备注' })
  // 回读的是后端那一笔的结果：弹窗要说「v2 已推上去」而不是「大概成功了吧」
  assert.equal(result.version, 2)
  assert.equal(result.status, 1)
  assert.equal(result.tagName, 'vorder-serive-sales_order_page-2')
})

test('merge request api maps list detail and refresh to the read-only backend endpoints', async () => {
  const calls: Array<{ url: string, method: string, body?: string }> = []
  const fetcher = async (url: string | URL | Request, init?: RequestInit) => {
    calls.push({ url: String(url), method: init?.method ?? 'GET', body: init?.body as string | undefined })
    return new Response(JSON.stringify({ code: 200, message: 'success', data: [], traceId: 't' }), {
      status: 200,
      headers: { 'content-type': 'application/json' },
    })
  }
  const api = createPlatformApi(createHttpClient({ baseUrl: '/api', tokenProvider: () => 'token', fetcher }))

  await api.mergeRequests.list({ pageNum: 1, pageSize: 20 })
  await api.mergeRequests.detail(12)
  await api.mergeRequests.refresh({ projectId: 3 })

  assert.equal(calls[0].url, '/api/lowcode/merge-requests?pageNum=1&pageSize=20')
  assert.equal(calls[1].url, '/api/lowcode/merge-requests/12')
  // 重算是 POST 而不是 GET：它会真连远端仓库（fetch 与 ls-remote），不能挂在「看一眼」上被预取
  assert.equal(calls[2].method, 'POST')
  assert.equal(calls[2].url, '/api/lowcode/merge-requests/refresh')
  assert.deepEqual(JSON.parse(calls[2].body ?? '{}'), { projectId: 3 })
})

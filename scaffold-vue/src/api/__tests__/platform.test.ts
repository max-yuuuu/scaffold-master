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

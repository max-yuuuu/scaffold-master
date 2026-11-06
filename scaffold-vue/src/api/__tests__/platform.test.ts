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

test('版本历史与回滚：读走 GET，写走 POST 且必须点明退回哪一版', async () => {
  const calls: Array<{ url: string, method: string, body?: string }> = []
  const fetcher = async (url: string | URL | Request, init?: RequestInit) => {
    calls.push({ url: String(url), method: init?.method ?? 'GET', body: init?.body as string | undefined })
    return new Response(JSON.stringify({
      code: 200,
      message: 'success',
      data: calls.length === 1
        ? { rows: [{ version: 2 }], total: 2, pageNum: 1, pageSize: 10 }
        : calls.length === 2
          ? { pageId: 59, version: 1, commitSha: 'b'.repeat(40), files: [{ path: 'x.vue', sha256: 'a', tier: 'GENERATED', rollbackable: true }] }
          : { pageId: 59, version: 3, rolledBackTo: 1, publishType: 'ROLLBACK', status: 1, commitSha: 'c'.repeat(40), tagName: 'vorder-serive-sales_order_page-3', branch: 'platform-generated', files: [], message: '已回滚到 v1' },
      traceId: 't',
    }), { status: 200, headers: { 'content-type': 'application/json' } })
  }
  const api = createPlatformApi(createHttpClient({ baseUrl: '/api', tokenProvider: () => 'token', fetcher }))

  await api.pages.versions(59, { pageNum: 1, pageSize: 10 })
  await api.pages.version(59, 1)
  const result = await api.pages.rollback(59, { version: 1, files: ['x.vue'] })

  assert.equal(calls[0].method, 'GET')
  assert.equal(calls[0].url, '/api/lowcode/pages/59/versions?pageNum=1&pageSize=10')
  assert.equal(calls[1].method, 'GET')
  assert.equal(calls[1].url, '/api/lowcode/pages/59/versions/1')
  // 回滚是写动作：POST，且 body 里必须带 version —— 少了它后端无从知道退回哪一版
  assert.equal(calls[2].method, 'POST')
  assert.equal(calls[2].url, '/api/lowcode/pages/59/rollback')
  assert.deepEqual(JSON.parse(calls[2].body ?? '{}'), { version: 1, files: ['x.vue'] })
  // 回滚产出的是「新的一版」，界面要读的是这两个号：v3 是新的，v1 是被退回的那一版
  assert.equal(result.version, 3)
  assert.equal(result.rolledBackTo, 1)
  assert.equal(result.tagName, 'vorder-serive-sales_order_page-3')
})

test('底座升级：读数是 GET，发起是 POST 且不带任何「顺手合进主干」的参数', async () => {
  const calls: Array<{ url: string, method: string, body?: string }> = []
  const fetcher = async (url: string | URL | Request, init?: RequestInit) => {
    calls.push({ url: String(url), method: init?.method ?? 'GET', body: init?.body as string | undefined })
    return new Response(JSON.stringify({
      code: 200,
      message: 'success',
      data: calls.length === 1
        // 数不出来时后端给 null，界面显示「未知」而不是 0 —— 这条形状是契约的一部分
        ? { baseScaffoldSha: 'a'.repeat(40), scaffoldSha: 'b'.repeat(40), upToDate: false, behindCommits: null, latest: null }
        : { projectCode: 'order-serive', upgradeBranch: 'platform-upgrade/bbbbbbb', commitSha: 'c'.repeat(40),
            clean: 3, conflict: 1, dropped: 0, unchanged: 279, binary: 0,
            conflictPaths: ['scaffold-vue/package.json'], droppedPaths: [], status: 0, message: '已推升级分支' },
      traceId: 't',
    }), { status: 200, headers: { 'content-type': 'application/json' } })
  }
  const api = createPlatformApi(createHttpClient({ baseUrl: '/api', tokenProvider: () => 'token', fetcher }))

  const status = await api.projects.baseStatus(23)
  const upgrade = await api.projects.baseUpgrade(23)
  await api.mergeRequests.refresh({ projectId: 23, sourceBranch: 'platform-upgrade/bbbbbbb' })

  assert.equal(calls[0].method, 'GET')
  assert.equal(calls[0].url, '/api/lowcode/projects/23/base-status')
  assert.equal(status.behindCommits, null)
  assert.equal(calls[1].method, 'POST')
  assert.equal(calls[1].url, '/api/lowcode/projects/23/base-upgrade')
  // 发起升级不收任何入参：期望值由后端按底座 checkout 现算，前端带一份就是「前端说是什么就算什么」
  assert.equal(calls[1].body, undefined)
  assert.equal(upgrade.upgradeBranch, 'platform-upgrade/bbbbbbb')
  assert.deepEqual(upgrade.conflictPaths, ['scaffold-vue/package.json'])
  assert.equal(calls[2].url, '/api/lowcode/merge-requests/refresh')
  assert.deepEqual(JSON.parse(calls[2].body ?? '{}'), { projectId: 23, sourceBranch: 'platform-upgrade/bbbbbbb' })
})

/**
 * 补打底座标记 tag：默认不提交号（平台按 .scaffold-project.json 自己认），要指定时才带。
 * 回执里的 locatedBy 必须回给界面 —— 「打对了没有」只有平台知道是哪一笔才说得出来。
 */
test('补打底座标记 tag 是 POST，留空就不带入参，指定时才带 commitSha', async () => {
  const calls: Array<{ url: string, method: string, body?: string }> = []
  const fetcher = async (url: string | URL | Request, init?: RequestInit) => {
    calls.push({ url: String(url), method: init?.method ?? 'GET', body: init?.body as string | undefined })
    return new Response(JSON.stringify({
      code: 200, message: 'success',
      data: { tagName: 'yuanda/platform-base/' + 'e'.repeat(40), commitSha: 'a'.repeat(40),
        locatedBy: '按 .scaffold-project.json 认出的最早那笔交付', message: '底座标记 tag 已补上' },
      traceId: 't',
    }), { status: 200, headers: { 'content-type': 'application/json' } })
  }
  const api = createPlatformApi(createHttpClient({ baseUrl: '/api', tokenProvider: () => 'token', fetcher }))

  const auto = await api.projects.stampBaseTag(23)
  const manual = await api.projects.stampBaseTag(23, 'a'.repeat(40))

  assert.equal(calls[0].method, 'POST')
  assert.equal(calls[0].url, '/api/lowcode/projects/23/base-tag')
  assert.deepEqual(JSON.parse(calls[0].body ?? '{}'), {})
  assert.deepEqual(JSON.parse(calls[1].body ?? '{}'), { commitSha: 'a'.repeat(40) })
  assert.equal(auto.locatedBy, '按 .scaffold-project.json 认出的最早那笔交付')
  assert.equal(manual.tagName, 'yuanda/platform-base/' + 'e'.repeat(40))
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

import test from 'node:test'
import assert from 'node:assert/strict'
import {
  UPGRADE_STATUS_PENDING,
  generateUpgradeMergeRequest,
  upgradeMrBlockReason,
  upgradeNextStep,
} from '../base-upgrade-flow.ts'

/**
 * 底座升级的后半截（方案 §5.2）。钉的是「界面上那颗按钮到底把哪条分支送出去」。
 *
 * 后端 `refresh` 从 PR 10 起就收可选的 sourceBranch，留空按口径回落项目的发布分支；
 * 而合并请求页与升级结果屏过去都只传 projectId —— 于是升级分支在界面上永远变不成待审请求，
 * 那句「下一步：在「合并请求」里选 platform-upgrade/xxx → main」指的是一件做不到的事。
 * 这里把它钉成契约：动作带的分支必须就是文案里那条，状态不对时根本不许发请求。
 */
test('待合并的升级记录生成合并请求时带上该升级分支作为来源分支', async () => {
  const payloads: Array<Record<string, unknown>> = []
  const result = await generateUpgradeMergeRequest(
    async (payload) => { payloads.push(payload) },
    23,
    { status: UPGRADE_STATUS_PENDING, upgradeBranch: 'platform-upgrade/099fd1f' },
  )

  assert.deepEqual(result, { ok: true, sourceBranch: 'platform-upgrade/099fd1f' })
  assert.equal(payloads.length, 1, '只应当发一次请求')
  assert.deepEqual(payloads[0], { projectId: 23, sourceBranch: 'platform-upgrade/099fd1f' })
})

test('指引文案里的分支名与实际送出的来源分支同源', async () => {
  const record = { status: UPGRADE_STATUS_PENDING, upgradeBranch: 'platform-upgrade/abc1234' }
  const payloads: Array<Record<string, unknown>> = []
  const result = await generateUpgradeMergeRequest(async (payload) => { payloads.push(payload) }, 7, record)

  const step = upgradeNextStep(record.upgradeBranch, 'main')
  assert.ok(step)
  assert.ok(step!.includes(record.upgradeBranch!), '文案不提这条分支就等于指引没有指向动作')
  assert.ok(result.ok)
  assert.ok(step!.includes(result.sourceBranch), '文案与动作必须指向同一条分支')
})

test('已合入主干的升级不再给生成动作', async () => {
  let called = 0
  const result = await generateUpgradeMergeRequest(
    async () => { called += 1 },
    23,
    { status: 1, upgradeBranch: 'platform-upgrade/099fd1f' },
  )

  assert.equal(called, 0, '已经合过的不该再去连远端')
  assert.equal(result.ok, false)
  assert.match((result as { reason: string }).reason, /已合入/)
})

test('没产生升级分支的那次不建请求，也不把空分支名送出去', async () => {
  let called = 0
  const result = await generateUpgradeMergeRequest(
    async () => { called += 1 },
    23,
    { status: 3, upgradeBranch: null },
  )

  assert.equal(called, 0)
  assert.equal(result.ok, false)
  assert.match((result as { reason: string }).reason, /没有/)
})

test('主干又前进的升级要求重新发起而不是硬合', async () => {
  const result = await generateUpgradeMergeRequest(
    async () => undefined,
    23,
    { status: 2, upgradeBranch: 'platform-upgrade/099fd1f' },
  )

  assert.equal(result.ok, false)
  assert.match((result as { reason: string }).reason, /重新发起/)
})

test('按钮的禁用说明与动作的拒绝理由是同一句话', async () => {
  const record = { status: 1, upgradeBranch: 'platform-upgrade/099fd1f' }
  const reason = upgradeMrBlockReason(record)
  assert.ok(reason, '已合入的记录要给出为什么不能再生成')

  const result = await generateUpgradeMergeRequest(async () => undefined, 23, record)
  assert.equal(result.ok, false)
  assert.equal((result as { reason: string }).reason, reason, '界面提示与动作拒绝必须是同一句话')
})

test('还没发起过升级时不说成「项目有问题」', () => {
  assert.match(upgradeMrBlockReason(null) ?? '', /还没发起过底座升级/)
})

test('后端拒绝的原文不被改写成通用失败提示', async () => {
  const reason = '远端已经没有升级分支 platform-upgrade/099fd1f：这条升级分支被删过或已经合过了，请重新发起一次底座升级'
  await assert.rejects(
    () => generateUpgradeMergeRequest(
      async () => { throw new Error(reason) },
      23,
      { status: UPGRADE_STATUS_PENDING, upgradeBranch: 'platform-upgrade/099fd1f' },
    ),
    (error: Error) => {
      assert.equal(error.message, reason, '界面要原样显示后端那句，人下一步能做的事写在那句话里')
      return true
    },
  )
})

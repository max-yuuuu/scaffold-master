/**
 * 底座升级的后半截（方案 §5.2）：升级分支推出去之后，怎么在界面上变成一条待审的合并请求。
 *
 * 后端 `MergeRequestService.refresh(projectId, sourceBranch)` 从 PR 10 起就认 `platform-upgrade/*`，
 * 留空则回落项目的发布分支 —— 所以「按钮少带一个字段」这件事在界面上不会报错，只会静默算成
 * 另一条分支的请求。这段逻辑抽出来是为了能被钉住：送错的分支会让人审一份不是升级内容的 diff。
 */

/** 待合并：升级分支已推出，等人从合并请求里合进主干（与后端 BaseUpgradeService.STATUS_PENDING 同一档） */
export const UPGRADE_STATUS_PENDING = 0

/** 状态文案只有一个出处，拒绝理由要说同一句话，不能两处各拼一遍 */
export const UPGRADE_STATUS_TEXT: Record<number, string> = {
  0: '待合并',
  1: '已合入主干',
  2: '主干又前进了（陈旧）',
  3: '没产生升级分支',
}

const REFUSAL_BY_STATUS: Record<number, string> = {
  1: `这条升级${UPGRADE_STATUS_TEXT[1]}，没有还要合进主干的内容`,
  2: '主干在预检之后又前进了，这条升级分支的结论已经陈旧：请重新发起一次底座升级',
  3: '这条升级没有产生分支（没有可自动下发的内容），不需要生成合并请求',
}

/** 一次升级记录里，这一刀只关心这两个字段 */
export interface UpgradeRecordLike {
  status: number
  upgradeBranch: string | null
}

export type UpgradeMrResult =
  | { ok: true, sourceBranch: string }
  | { ok: false, reason: string }

/**
 * 这条记录能不能生成合并请求，不能的话为什么。
 *
 * 按钮的禁用说明与动作的拒绝理由都出自这里 —— 否则同一件事会在界面上出现两种说法。
 */
export function upgradeMrBlockReason(record: UpgradeRecordLike | null | undefined): string | null {
  if (!record) return '还没发起过底座升级，没有要合的分支'
  if (record.status !== UPGRADE_STATUS_PENDING) {
    return REFUSAL_BY_STATUS[record.status] ?? `这条升级的状态是「${record.status}」，不在待合并`
  }
  if (!record.upgradeBranch) return REFUSAL_BY_STATUS[3]
  return null
}

/** 那句「下一步」—— 它必须指向同一个动作，见测试里的同源断言 */
export function upgradeNextStep(upgradeBranch: string | null, mainBranch?: string | null): string | null {
  if (!upgradeBranch) return null
  return `下一步：在「合并请求」里选 ${upgradeBranch} → ${mainBranch || '主干'} 审这份差异，确认后才进主干。`
}

/**
 * 为这条升级记录生成（或重算）合并请求。
 *
 * @param refresh 后端的 `POST /lowcode/merge-requests/refresh`，页面注入进来以便测试替身
 * @throws 后端的拒绝原样抛出：远端已无该分支、零差异这两类，下一步要做的事就写在人家那句话里
 */
export async function generateUpgradeMergeRequest(
  refresh: (payload: { projectId: number, sourceBranch: string }) => Promise<unknown>,
  projectId: number | null | undefined,
  record: UpgradeRecordLike | null | undefined,
): Promise<UpgradeMrResult> {
  if (projectId == null) {
    return { ok: false, reason: '先选项目再生成：合并请求是按项目的仓库算的' }
  }

  const blocked = upgradeMrBlockReason(record)
  if (blocked) return { ok: false, reason: blocked }

  const sourceBranch = (record as UpgradeRecordLike).upgradeBranch as string
  await refresh({ projectId, sourceBranch })
  return { ok: true, sourceBranch }
}

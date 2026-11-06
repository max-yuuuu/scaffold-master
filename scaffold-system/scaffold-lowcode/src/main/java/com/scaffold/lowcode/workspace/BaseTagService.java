package com.scaffold.lowcode.workspace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.lowcode.project.mapper.LcProjectMapper;
import com.scaffold.lowcode.publish.PublishSiteResolver;
import com.scaffold.lowcode.repo.RepoUrlValidator;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 人工补打底座标记 tag（方案 §5.2；10G 真机演练留下的那个入口缺口）。
 *
 * <p>为什么要有这个动作：三方合并的 base 侧是按 {@code yuanda/platform-base/{底座号}} 这个 tag 名去
 * <b>项目仓库</b>里取的。存量项目（初始化早于「初始化打 tag」那段代码）远端根本没有它，而
 * {@code initialize} 在已就绪的项目上会拒绝重来 —— 于是这些项目的底座升级永久封死，只能有人跑到命令行上
 * 手工 {@code git tag && git push}。演练时就是这么补的，界面该给这一步一个位置。
 *
 * <p><b>tag 指向哪一笔不让人猜</b>：交付那一笔自己带着 {@code .scaffold-project.json}，里面的
 * {@code baseScaffoldSha} 与库里那个号一致的就是它。遍历从新往旧，但命中后继续往旧走、取<b>最早</b>那一笔
 * （同一次交付可能连着推两笔，base 要的是「那次刚给出去的那份」），撞到记着别的底座号的 marker 才停 ——
 * 那说明已经走到上一轮交付了。认不出来就拒绝并让人指定提交号：猜错一次，之后每一轮三方合并都建立在
 * 错的 base 上，而界面上看起来一切正常。
 */
@Service
public class BaseTagService {

    /** 往旧最多看这么多笔：跑了几年的发布分支有几万笔提交，逐笔读 marker 不能没有上界 */
    private static final int HISTORY_SCAN_LIMIT = 400;

    private final LcProjectMapper projectMapper;
    private final GitGateway gateway;
    private final WorkspacePaths paths;
    private final ObjectMapper objectMapper;

    public BaseTagService(LcProjectMapper projectMapper, GitGateway gateway, WorkspacePaths paths,
                          ObjectMapper objectMapper) {
        this.projectMapper = projectMapper;
        this.gateway = gateway;
        this.paths = paths;
        this.objectMapper = objectMapper;
    }

    /**
     * @param requestedCommitSha 人指定的提交号；{@code null} 或空表示让平台按项目标记文件定位
     * @return tag 名、打在哪一笔、这笔是怎么定出来的（界面要说清「打对了没有」）
     */
    public Map<String, Object> stamp(Long projectId, String requestedCommitSha) {
        Map<String, Object> project = projectMapper.selectProjectForSync(projectId);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        String baseSha = text(project.get("baseScaffoldSha"));
        if (baseSha == null || baseSha.isBlank()) {
            throw new BusinessException("项目「" + project.get("projectCode") + "」记不到底座提交号："
                    + "连 tag 名都拼不出来。它是在底座 checkout 没带 .git 的版本上初始化的，"
                    + "请把带 .git 的底座仓库挂给平台后清空工作区重新初始化");
        }
        String projectCode = text(project.get("projectCode"));
        String genBranch = text(project.get("genBranch"));
        String repoUrl = RepoUrlValidator.normalize(text(project.get("repoUrl")));
        String tagName = PlatformBaseTag.name(baseSha);

        List<RemoteRef> refs = gateway.lsRemote(repoUrl);
        String existing = refs.stream().filter(ref -> tagName.equals(ref.tagName())).map(RemoteRef::commitSha)
                .findFirst().orElse(null);
        if (existing != null) {
            throw new BusinessException("远端已经有底座标记 tag " + tagName + "（指向 "
                    + PublishSiteResolver.abbreviate(existing) + "）：平台不移动它 —— 被改掉的正是那一轮交付的 base 侧，"
                    + "之后每次三方合并都会算错");
        }
        if (PublishSiteResolver.remoteSha(refs, genBranch) == null) {
            throw new BusinessException("远端没有发布分支 " + genBranch + "：固定代码还没推上去，"
                    + "先初始化再补打这个 tag");
        }
        Path workspace = paths.of(projectCode);
        if (!Files.isDirectory(workspace.resolve(".git"))) {
            throw new BusinessException("平台工作区不在本机（" + paths.relative(projectCode)
                    + "），读不到交付历史来定位那一笔：请指定提交号，或先让工作区重建起来");
        }

        boolean byHuman = requestedCommitSha != null && !requestedCommitSha.isBlank();
        String target = byHuman ? requestedCommitSha.trim() : locateDelivery(workspace, genBranch, baseSha);
        // 打 tag 走的是网关那条「重新 clone 目标分支」的路：工作区这条引用一点都不动
        if (!gateway.stampBaseTag(workspace, repoUrl, genBranch, tagName, target,
                PlatformBaseTag.message(baseSha) + "\n\n由人工在项目抽屉「补打底座标记 tag」里补上："
                        + "这个 tag 之前不在远端，本项目的底座升级因此做不了（三方合并缺 base 侧）。")) {
            throw new BusinessException("就在刚才远端出现了这个 tag（" + tagName + "）：平台不移动它，请刷新后核对指向的是哪一笔");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tagName", tagName);
        out.put("commitSha", target);
        out.put("locatedBy", byHuman ? "人指定的提交号" : "按 .scaffold-project.json 认出的最早那笔交付");
        out.put("message", "底座标记 tag 已补上：" + tagName + " → " + PublishSiteResolver.abbreviate(target)
                + "。现在可以发起底座升级了");
        // 状态没变（还是就绪），这里不回写 lc_project：tag 是仓库侧的事实，库里记的底座号本来就是对的
        return out;
    }

    /**
     * 在发布分支历史里认出「当初交付那一版 A 档」的提交。
     *
     * <p>前提写在这：底座号沿历史是单调变新的（每次交付都记当时更新的底座号），所以撞到记着<b>别的</b>
     * 底座号的 marker 就可以停 —— 再往旧就是上一轮交付了，那一笔的 A 档不是本项目现在认的这一版。
     */
    private String locateDelivery(Path workspace, String genBranch, String baseSha) {
        gateway.fetch(workspace);
        List<String> history = gateway.revList(workspace, "origin/" + genBranch, HISTORY_SCAN_LIMIT);
        if (history.isEmpty()) {
            throw new BusinessException("发布分支 " + genBranch + " 的历史读不到（远端刚初始化？），请指定提交号");
        }
        String found = null;
        for (String sha : history) {
            String recorded = markerBaseShaAt(workspace, sha);
            if (recorded == null) {
                continue;                       // 生成页与人工提交不带项目标记，继续往旧
            }
            if (baseSha.equals(recorded)) {
                found = sha;                    // 命中后不回头：要的是这一版交付里最早那一笔
                continue;
            }
            break;                              // 走到上一轮交付，这一段结束了
        }
        if (found == null) {
            throw new BusinessException("在发布分支最近 " + history.size() + " 笔里没找到记着底座号 "
                    + PublishSiteResolver.abbreviate(baseSha) + " 的那笔交付，不敢替你猜："
                    + "请指定提交号（那一笔的特征是 .scaffold-project.json 里的 baseScaffoldSha 等于 "
                    + PublishSiteResolver.abbreviate(baseSha) + "）");
        }
        return found;
    }

    /** 读某一笔上的项目标记文件；没有这个文件返回 null，读不成 JSON 则停手 —— 半截判据比没有判据更危险 */
    private String markerBaseShaAt(Path workspace, String sha) {
        Map<String, String> files = gateway.fileContents(workspace, sha, List.of(ScaffoldTrimPolicy.MARKER_FILE));
        String raw = files.get(ScaffoldTrimPolicy.MARKER_FILE);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            String value = node.path("baseScaffoldSha").asText(null);
            return value == null || value.isBlank() ? null : value;
        } catch (Exception e) {
            throw new BusinessException("那笔提交上的 " + ScaffoldTrimPolicy.MARKER_FILE + " 读不成 JSON（"
                    + PublishSiteResolver.abbreviate(sha) + "）：" + e.getMessage()
                    + "。定位不了就不打，请指定提交号");
        }
    }

    private static String text(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }
}

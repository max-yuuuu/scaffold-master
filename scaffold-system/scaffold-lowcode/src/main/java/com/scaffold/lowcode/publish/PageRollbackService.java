package com.scaffold.lowcode.publish;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.lowcode.generate.GeneratedFile;
import com.scaffold.lowcode.generate.GeneratedFileWriter;
import com.scaffold.lowcode.generate.GeneratedPaths;
import com.scaffold.lowcode.page.mapper.LcPageMapper;
import com.scaffold.lowcode.project.mapper.LcProjectMapper;
import com.scaffold.lowcode.workspace.GitGateway;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 页面回滚（方案 §5.4）：把某一版的内容还原成<b>一笔新提交</b>推到发布分支。
 *
 * <p>三条口径决定了这个类为什么长这样：
 * <ul>
 *   <li><b>一律 {@code revert} 出新提交，不 {@code reset} 倒带</b>：分支历史是别人已经 fetch 过的，
 *       倒带就要强推，而平台从不强推。回滚后 v3 的内容等于 v1，但 v2 仍在历史里、仍能被指出来、
 *       仍能被回滚过去 —— 「这次撤回了什么」在历史上是一个可指的提交，不是一次悄悄改写；</li>
 *   <li><b>回滚也是一版</b>：新版本号 {@code current + 1}、{@code publish_type = ROLLBACK}、照样打 tag。
 *       把 {@code current_version} 改回旧号会让「库里说现在是 v1」与「仓库里 v2 那笔提交还在」互相矛盾，
 *       而下一次发布的版本号就是这么算的；</li>
 *   <li><b>只写发布分支</b>：主干上的内容要靠合并请求由人确认，回滚不绕过那道门。要让撤回进主干，
 *       就在合并请求里把这笔回滚提交合进去 —— 审的人看到的清单自然会说「这次是把哪些内容撤回去」。</li>
 * </ul>
 *
 * <p>取数走仓库而不是重新生成：{@code design_snapshot} 存的是当时的设计，而模板会改。按今天的模板
 * 重跑一遍拿到的不是「那一版长什么样」，而是「那一版今天应该长什么样」—— 这两件事不一样，
 * 而回滚要的是前者。C 档扩展点不参与：平台不生成它，也就无权把人的代码还原成历史里那一份。
 */
@Service
public class PageRollbackService {

    private final LcPageMapper pageMapper;
    private final LcProjectMapper projectMapper;
    private final PageVersionService versions;
    private final PublishSiteResolver sites;
    private final GitGateway gateway;
    private final PublishLedger ledger;
    private final ObjectMapper objectMapper;

    public PageRollbackService(LcPageMapper pageMapper, LcProjectMapper projectMapper, PageVersionService versions,
                               PublishSiteResolver sites, GitGateway gateway, PublishLedger ledger,
                               ObjectMapper objectMapper) {
        this.pageMapper = pageMapper;
        this.projectMapper = projectMapper;
        this.versions = versions;
        this.sites = sites;
        this.gateway = gateway;
        this.ledger = ledger;
        this.objectMapper = objectMapper;
    }

    /**
     * @param version 要退回的那一版，必填
     * @param files   只还原这几个文件（文件级）；为空就是整页回滚。路径必须属于那一版，且在清单里归 B 档
     * @return 新那一版：版本号、提交号、tag、还原了哪些文件，以及一句给人读的话
     */
    public Map<String, Object> rollback(Long pageId, Integer version, List<String> files) {
        Map<String, Object> page = pageMapper.selectPageForPublish(pageId);
        if (page == null) {
            throw new BusinessException("页面不存在");
        }
        if (number(page.get("status"), 1) != 1) {
            throw new BusinessException("页面「" + page.get("pageCode") + "」已停用，先启用再回滚");
        }
        int current = number(page.get("currentVersion"), 0);
        if (current == 0) {
            throw new BusinessException("页面「" + page.get("pageCode") + "」还没发布成功过，没有可回滚的历史");
        }
        if (version == null || version < 1) {
            throw new BusinessException("回滚要指明退回哪一版，且版本号从 1 开始");
        }
        if (version == current) {
            throw new BusinessException("v" + version + " 就是这个页面当前的那一版，回滚它等于什么都不做："
                    + "要退回去请选更早的一版");
        }
        Map<String, Object> project = requireProject(page);
        PageVersionService.VersionRow target = versions.require(numberAsLong(page.get("id")), version);
        List<String> picked = pickFiles(target, files);

        String projectCode = text(project.get("projectCode"));
        String pageCode = text(page.get("pageCode"));
        PublishSiteResolver.Site site = sites.open(project, "回滚", "页面 " + pageCode);
        int next = current + 1;
        String tagName = PublishTag.name(projectCode, pageCode, next);
        if (PublishSiteResolver.remoteTagName(site.refs(), tagName) != null) {
            throw new BusinessException("远端已经有版本标记 " + tagName + "，而库里这个页面的当前版本是 " + current
                    + "：平台记录与仓库不一致，这次回滚停手，请先核对项目的发布分支");
        }

        // 先对账再落盘：仓库里那一笔的内容与库里记的哈希必须一致，不一致就说明「账」与「仓库」漂开了，
        // 而回滚正是要照着这份账写仓库 —— 这时候写下去的内容没人核对过
        Map<String, String> contents = gateway.fileContents(site.workspace(), target.commitSha(), picked);
        List<String> missing = picked.stream().filter(path -> !contents.containsKey(path)).toList();
        if (!missing.isEmpty()) {
            throw new BusinessException("v" + version + " 的清单里有 " + missing.size() + " 个文件在提交 "
                    + PublishSiteResolver.abbreviate(target.commitSha()) + " 里查不到：" + String.join("、", missing)
                    + "。版本快照与仓库对不上，回滚停手，请先核对这一版是怎么发出去的");
        }
        List<String> mismatched = picked.stream()
                .filter(path -> !ContentHash.sha256(contents.get(path)).equals(target.files().get(path)))
                .toList();
        if (!mismatched.isEmpty()) {
            throw new BusinessException("提交 " + PublishSiteResolver.abbreviate(target.commitSha())
                    + " 里这几个文件的内容与版本快照对不上：" + String.join("、", mismatched)
                    + "。可能是那一版之后有人在发布分支上直接改过它们，回滚不替人猜哪个是真的");
        }

        List<GeneratedFile> restore = picked.stream().map(path -> GeneratedFile.of(path, contents.get(path))).toList();
        List<Map<String, Object>> fileRows = fileRows(restore, GeneratedFileWriter.write(site.workspace(), restore));
        String changeDesc = "回滚到 v" + version + (files == null || files.isEmpty()
                ? "" : "（只还原 " + picked.size() + " 个文件）");
        PublishLedger.Context context = new PublishLedger.Context(numberAsLong(page.get("id")),
                numberAsLong(project.get("id")), next, "ROLLBACK", site.branch(), json(fileRows),
                target.designSnapshot(), changeDesc);

        String committed = gateway.commitAll(site.workspace(), commitMessage(projectCode, page, version, next));
        // 与发布同一套两分法：远端已经是这份内容才是真无变更；否则是上次推失败的提交还压在本地，必须补推
        String targetSha = committed != null ? committed : site.localSha();
        if (committed == null && targetSha.equals(site.remoteSha())) {
            String message = "内容与 v" + version + " 已经一致，未产生提交";
            ledger.noChange(context, targetSha, message);
            return result(page, project, next, version, 2, targetSha, null, site.branch(), fileRows, message);
        }

        push(context, site, targetSha);
        String confirmed = PublishSiteResolver.remoteSha(gateway.lsRemote(site.repoUrl()), site.branch());
        if (!targetSha.equals(confirmed)) {
            String why = "推送后回读远端 " + site.branch() + " 得到的是 "
                    + (confirmed == null ? "无该分支" : confirmed) + "，与本次回滚提交 "
                    + PublishSiteResolver.abbreviate(targetSha) + " 不一致";
            ledger.failed(context, targetSha, "回滚提交留在本地工作区、没有推到远端，重试回滚会把它推上去", why);
            throw new BusinessException(why + "：平台不强推，也没有把这一版记进版本历史，请核对远端后重试");
        }
        tag(context, site, targetSha, tagName, projectCode, pageCode, next);
        // 快照以「当前那一版」为底，只把还原过的路径换成目标版的哈希：文件级回滚之后，
        // 新的一版仍然是「整页的清单」，下一次回滚照它取数才不会漏文件
        ledger.published(context, targetSha, tagName, json(snapshot(next, current, page, target, contents)),
                "已回滚到 v" + version + "，新的 v" + next + " 落在 " + site.branch() + "（还原 "
                        + fileRows.size() + " 个文件）");
        return result(page, project, next, version, 1, targetSha, tagName, site.branch(), fileRows,
                "已回滚到 v" + version);
    }

    /* ── 选文件 ── */

    /**
     * 整页回滚就是那一版里所有 B 档文件；文件级要逐个验：不属于那一版的路径、以及 C 档扩展点，都当场拒。
     *
     * <p>判据用 {@link GeneratedPaths#tier} 而不是调用方传没传：路径是唯一事实源，而「平台不生成它
     * 就无权还原它」这条不能靠前端自觉。
     */
    private List<String> pickFiles(PageVersionService.VersionRow target, List<String> files) {
        if (files == null || files.isEmpty()) {
            List<String> all = new ArrayList<>();
            target.files().forEach((path, sha) -> {
                if (GeneratedPaths.tier(path) == GeneratedFile.Tier.GENERATED) {
                    all.add(path);
                }
            });
            if (all.isEmpty()) {
                throw new BusinessException("v" + target.version() + " 的清单里没有可还原的生成文件：这一版"
                        + "只有扩展点，平台不还原自己没生成的东西");
            }
            return all;
        }
        List<String> unknown = files.stream().filter(path -> !target.files().containsKey(path)).toList();
        if (!unknown.isEmpty()) {
            throw new BusinessException("这些文件不在 v" + target.version() + " 的产物清单里："
                    + String.join("、", unknown) + "。回滚只能还原那一版里平台生成过的文件");
        }
        List<String> extension = files.stream()
                .filter(path -> GeneratedPaths.tier(path) == GeneratedFile.Tier.EXTENSION).toList();
        if (!extension.isEmpty()) {
            throw new BusinessException("这些是 C 档扩展点，不参与回滚：" + String.join("、", extension)
                    + "。平台不生成它，也就无权把人在里面写的代码还原成历史里那一份 —— 要改请回项目仓库改");
        }
        return List.copyOf(files);
    }

    /** 新一版的「路径 → 哈希」：当前版的清单为底，被还原的路径换成目标版那份 */
    private Map<String, String> snapshot(int next, int current, Map<String, Object> page,
                                         PageVersionService.VersionRow target, Map<String, String> contents) {
        Map<String, String> merged = new LinkedHashMap<>(versions.require(numberAsLong(page.get("id")), current).files());
        contents.keySet().forEach(path -> merged.put(path, target.files().get(path)));
        return merged;
    }

    /* ── 现场与记账 ── */

    private Map<String, Object> requireProject(Map<String, Object> page) {
        Map<String, Object> project = projectMapper.selectProjectForSync(numberAsLong(page.get("projectId")));
        if (project == null) {
            throw new BusinessException("页面归属的项目不存在，无法定位仓库");
        }
        if (number(project.get("status"), 1) != 1) {
            throw new BusinessException("项目「" + project.get("projectCode") + "」已停用，先启用再回滚");
        }
        return project;
    }

    private void push(PublishLedger.Context context, PublishSiteResolver.Site site, String targetSha) {
        try {
            gateway.pushBranch(site.workspace(), site.branch());
        } catch (RuntimeException e) {
            String error = "推送发布分支 " + site.branch() + " 失败：" + e.getMessage();
            String message = "提交 " + PublishSiteResolver.abbreviate(targetSha) + " 留在本地工作区、没有推到远端，"
                    + "重试回滚会把它推上去";
            ledger.failed(context, targetSha, message, error);
            throw new BusinessException(error + "：" + message);
        }
    }

    /** tag 没推上去就不认这一版：没有锚点，下一次「回滚到它」根本无从定位 */
    private void tag(PublishLedger.Context context, PublishSiteResolver.Site site, String targetSha, String tagName,
                     String projectCode, String pageCode, int version) {
        try {
            gateway.tagCommit(site.workspace(), tagName, targetSha,
                    PublishTag.message(projectCode, pageCode, version, targetSha));
            gateway.pushTag(site.workspace(), tagName);
        } catch (RuntimeException e) {
            String message = "版本标记 " + tagName + " 没推上去：" + e.getMessage()
                    + "。分支已推送，但这一版没有记进版本历史（tag 是合并与回滚的锚点）";
            ledger.failed(context, targetSha, message, e.getMessage());
            throw new BusinessException(message + "：请核对远端的 tag 保护，再用同一版本号重试");
        }
    }

    private Map<String, Object> result(Map<String, Object> page, Map<String, Object> project, int version,
                                       int rolledBackTo, int status, String commitSha, String tagName, String branch,
                                       List<Map<String, Object>> fileRows, String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pageId", page.get("id"));
        out.put("pageCode", page.get("pageCode"));
        out.put("projectId", project.get("id"));
        out.put("projectCode", project.get("projectCode"));
        out.put("version", version);
        out.put("rolledBackTo", rolledBackTo);
        out.put("publishType", "ROLLBACK");
        out.put("status", status);
        out.put("commitSha", commitSha);
        out.put("tagName", tagName);
        out.put("branch", branch);
        out.put("files", fileRows);
        out.put("message", message);
        return out;
    }

    /** 每个还原的文件一行：路径 + 档位 + 落盘动作 + 内容哈希，形状与发布的产物清单一致 */
    private List<Map<String, Object>> fileRows(List<GeneratedFile> restore, List<GeneratedFileWriter.Written> writes) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (GeneratedFileWriter.Written write : writes) {
            GeneratedFile file = restore.stream().filter(item -> item.path().equals(write.path())).findFirst()
                    .orElseThrow(() -> new BusinessException("还原清单与落盘结果对不上：" + write.path()));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("path", write.path());
            row.put("tier", file.tier().name());
            row.put("action", write.action().name());
            row.put("sha256", ContentHash.sha256(file.content()));
            rows.add(row);
        }
        return rows;
    }

    private String commitMessage(String projectCode, Map<String, Object> page, int from, int to) {
        return "fix(" + page.get("pageCode") + "): 回滚 " + page.get("pageName") + " 到 v" + from
                + "（新的 v" + to + "，元搭低代码平台，项目 " + projectCode + "）";
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException("回滚结果写不成 JSON：" + e.getMessage());
        }
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int number(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? defaultValue : Integer.parseInt(String.valueOf(value));
    }

    private static long numberAsLong(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }
}

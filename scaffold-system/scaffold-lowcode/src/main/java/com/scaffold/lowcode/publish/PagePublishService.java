package com.scaffold.lowcode.publish;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.lowcode.design.DesignContext;
import com.scaffold.lowcode.design.PageDesign;
import com.scaffold.lowcode.design.PageTarget;
import com.scaffold.lowcode.generate.GeneratedFile;
import com.scaffold.lowcode.generate.GeneratedFileWriter;
import com.scaffold.lowcode.generate.ModelCodeGenerator;
import com.scaffold.lowcode.model.mapper.LcFieldMapper;
import com.scaffold.lowcode.model.mapper.LcModelMapper;
import com.scaffold.lowcode.page.mapper.LcPageMapper;
import com.scaffold.lowcode.project.domain.ProjectSyncStatus;
import com.scaffold.lowcode.project.mapper.LcProjectMapper;
import com.scaffold.lowcode.repo.RepoUrlValidator;
import com.scaffold.lowcode.workspace.GitGateway;
import com.scaffold.lowcode.workspace.RemoteRef;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 页面发布（方案 §5.1）：设计 → 生成 → 落盘 → 提交 → 推送 → tag → 记账。
 *
 * <p>「发布」在这个平台里只有这一件事：把人设计出来的代码推到租户仓库的<b>发布分支</b>。不碰主干、
 * 不部署、不连项目的库（§〇之二 第 2 条：平台不托管运行时）；合并是 PR 8 由人显式发起的另一个动作。
 *
 * <p>几条不那么显然的口径，代码里各有一个拒绝分支等着：
 * <ul>
 *   <li><b>发布时把设计重新对一遍模型</b>：库里那份是上次保存时对过的，字段可能在这中间被删过；
 *       放过就是让生成器拿不存在的字段拼产物；</li>
 *   <li><b>工作区必须是干净的</b>：提交是整目录暂存，人在工作区里改过没提交的东西会被这次发布一起推进
 *       发布分支 —— 把不属于他的改动挂到他的动作上；</li>
 *   <li><b>本地与远端不一致时先问清是谁前进的</b>：远端仍停在平台上次推上去的那个提交，就是上次推送没成功、
 *       这次补推；否则是别人往发布分支推过东西，平台不强推也不自动合并，让人先重建工作区（§5.3 安全边界）；</li>
 *   <li><b>先落到仓库、再记库里的账</b>：版本号与 tag 都在推送成功之后才写。反过来就会留下
 *       「库里说发过 v3、仓库里查无此提交」的状态，而 PR 9 的回滚正是照着这句话取内容的；</li>
 *   <li><b>无变更不产生提交</b>：内容一样就是没动过，硬造一个空提交只会把发布分支的历史变成噪音。</li>
 * </ul>
 */
@Service
public class PagePublishService {

    private final LcPageMapper pageMapper;
    private final LcModelMapper modelMapper;
    private final LcFieldMapper fieldMapper;
    private final LcProjectMapper projectMapper;
    private final ModelCodeGenerator generator;
    private final PublishLedger ledger;
    private final GitGateway gateway;
    private final PublishSiteResolver sites;
    private final ObjectMapper objectMapper;

    public PagePublishService(LcPageMapper pageMapper, LcModelMapper modelMapper, LcFieldMapper fieldMapper,
                              LcProjectMapper projectMapper, ModelCodeGenerator generator, PublishLedger ledger,
                              GitGateway gateway, PublishSiteResolver sites, ObjectMapper objectMapper) {
        this.pageMapper = pageMapper;
        this.modelMapper = modelMapper;
        this.fieldMapper = fieldMapper;
        this.projectMapper = projectMapper;
        this.generator = generator;
        this.ledger = ledger;
        this.gateway = gateway;
        this.sites = sites;
        this.objectMapper = objectMapper;
    }

    /**
     * @param force 显式放行「发布前 C 档引用体检」报出来的可疑引用（方案 §5.2 第 2 条）。
     *              报出来的东西可能是同名局部变量或注释，平台不替人猜，但要人明确说「我看过」。
     * @return 发布结果：版本号、提交号、tag、落盘的产物清单、体检报出来但仍放行的告警与一句给人读的话
     */
    public Map<String, Object> publish(Long pageId, boolean force, String changeDesc) {
        Map<String, Object> page = pageMapper.selectPageForPublish(pageId);
        if (page == null) {
            throw new BusinessException("页面不存在");
        }
        if (number(page.get("status"), 1) != 1) {
            throw new BusinessException("页面「" + page.get("pageName") + "」已停用，不参与发布");
        }
        Map<String, Object> model = modelMapper.selectModelForGenerate(numberAsLong(page.get("modelId")));
        if (model == null) {
            throw new BusinessException("页面绑定的模型不存在，请重新绑定模型");
        }
        if (number(model.get("status"), 1) != 1) {
            throw new BusinessException("模型「" + model.get("modelCode") + "」已停用，先启用再发布");
        }
        Map<String, Object> project = requireProject(page);
        // 现场守卫与回滚共用一份（PublishSiteResolver）：两条链路都是「整目录暂存 + 提交 + 推送」，
        // 守卫抄两份的话改一条要记得改两处，这种约定迟早漂
        PublishSiteResolver.Site site = sites.open(project, "发布", "页面 " + text(page.get("pageCode")));

        int current = number(page.get("currentVersion"), 0);
        int next = current + 1;
        String projectCode = text(project.get("projectCode"));
        String pageCode = text(page.get("pageCode"));
        String tagName = PublishTag.name(projectCode, pageCode, next);
        if (PublishSiteResolver.remoteTagName(site.refs(), tagName) != null) {
            // 库里记的版本与仓库里的 tag 对不上，不是这次的内容冲突，是两边状态漂开了。
            // 平台不挪 tag（挪掉就改了那一版的锚点），也不覆盖那一版
            throw new BusinessException("远端已经有版本标记 " + tagName + "，而库里这个页面的当前版本是 " + current
                    + "：平台记录与仓库不一致，本次发布停手，请先核对项目的发布分支");
        }

        List<Map<String, Object>> fieldRows = fieldMapper.selectByModel(numberAsLong(model.get("id")));
        // 发布时重新对一遍设计，而不是相信库里那份：规则与报错形状见 PageDesign
        PageDesign design = PageDesign.parse(text(page.get("design")),
                DesignContext.of(text(model.get("modelCode")), fieldRows));
        PageTarget target = new PageTarget(text(page.get("pageName")), text(page.get("routePath")),
                text(page.get("menuIcon")), integer(page.get("menuSort")), design);
        List<GeneratedFile> files = generator.renderFiles(model, fieldRows, target);
        // 发布前拿这次的产物对一遍 C 档里的引用：人删了字段而扩展点还在用它，等交付项目编译失败时
        // 人已经在合并请求里了，而 diff 只会显示「生成物变了」，不会告诉他「你少了一个字段」（§5.2）
        List<ExtensionReferenceCheck.Stale> stale = staleReferences(site.workspace(), files);
        if (!stale.isEmpty() && !force) {
            throw new BusinessException(ExtensionReferenceCheck.describe(stale)
                    + "\n我已确认这些只是同名变量或注释、仍要发布，就带 force 再来一次");
        }
        List<Map<String, Object>> fileRows = fileRows(files, GeneratedFileWriter.write(site.workspace(), files));
        String filesJson = json(fileRows);
        PublishLedger.Context context = new PublishLedger.Context(pageId, numberAsLong(project.get("id")), next,
                current == 0 ? "CREATE" : "UPDATE", site.branch(), filesJson, text(page.get("design")), trim(changeDesc));

        String committed = gateway.commitAll(site.workspace(), commitMessage(projectCode, page, next));
        // 提交没产生改动时分两种：远端已经是这份内容（真无变更），和上次推失败的那个提交还压在本地（必须补推）。
        // 后者如果被当成无变更放过，一次推送失败就把这个页面永久卡在「库里说没发过、分支上有个没人认领的提交」
        String targetSha = committed != null ? committed : site.localSha();
        if (committed == null && targetSha.equals(site.remoteSha())) {
            String message = "内容与 " + site.branch() + " 一致，未产生提交";
            ledger.noChange(context, targetSha, message);
            return result(project, page, current, 2, targetSha, null, site.branch(), fileRows, stale, message);
        }

        push(context, site, targetSha);
        String confirmed = PublishSiteResolver.remoteSha(gateway.lsRemote(site.repoUrl()), site.branch());
        if (!targetSha.equals(confirmed)) {
            String why = "推送后回读远端 " + site.branch() + " 得到的是 "
                    + (confirmed == null ? "无该分支" : confirmed) + "，与本次提交 " + PublishSiteResolver.abbreviate(targetSha) + " 不一致";
            ledger.failed(context, targetSha, "远端没有拿到这次提交，这一版没记进版本历史：" + why, why);
            throw new BusinessException(why + "：平台不强推，也没有把这一版记进版本历史，请核对远端后重试");
        }

        tag(context, site, targetSha, tagName, projectCode, pageCode);
        String message = "已发布 v" + next + " 到 " + site.branch() + "（" + fileRows.size() + " 个产物："
                + count(fileRows, "CREATED") + " 新建 / " + count(fileRows, "OVERWRITTEN") + " 覆盖 / "
                + count(fileRows, "SKIPPED") + " 跳过）";
        ledger.published(context, targetSha, tagName, json(hashes(fileRows)), message);
        return result(project, page, next, 1, targetSha, tagName, site.branch(), fileRows, stale, message);
    }

    /* ── 现场 ── */

    private Map<String, Object> requireProject(Map<String, Object> page) {
        Map<String, Object> project = projectMapper.selectProjectForSync(numberAsLong(page.get("projectId")));
        if (project == null) {
            throw new BusinessException("页面归属的项目不存在，无法定位仓库");
        }
        if (number(project.get("status"), 1) != 1) {
            throw new BusinessException("项目「" + project.get("projectCode") + "」已停用，先启用再发布");
        }
        return project;
    }

    private void push(PublishLedger.Context context, PublishSiteResolver.Site site, String targetSha) {
        try {
            gateway.pushBranch(site.workspace(), site.branch());
        } catch (RuntimeException e) {
            String error = "推送发布分支 " + site.branch() + " 失败：" + e.getMessage();
            String message = "提交 " + PublishSiteResolver.abbreviate(targetSha) + " 留在本地工作区、没有推到远端，重试发布会把它推上去";
            ledger.failed(context, targetSha, message, error);
            throw new BusinessException(error + "：" + message);
        }
    }

    /**
     * 创建并推送版本 tag。
     *
     * <p>{@code tagCommit} 返回 {@code false} 表示本地已有同名 tag —— 那是上一次「tag 建了但没推上去」
     * 留下的，它指向的就是本次这个提交（平台从不挪动已存在的 tag），照原样推上去就是对的锚点。
     */
    private void tag(PublishLedger.Context context, PublishSiteResolver.Site site, String targetSha,
                     String tagName,
                     String projectCode, String pageCode) {
        String error = null;
        try {
            gateway.tagCommit(site.workspace(), tagName, targetSha,
                    PublishTag.message(projectCode, pageCode, context.version(), targetSha));
            gateway.pushTag(site.workspace(), tagName);
        } catch (RuntimeException e) {
            error = "版本标记 " + tagName + " 没推上去：" + e.getMessage();
        }
        if (error != null) {
            // 分支已经推上去了，但这一版不认：没有 tag 就没有锚点，合并与回滚都无从定位
            String message = error + "。分支已推送，但这一版没有记进版本历史（tag 是合并与回滚的锚点）";
            ledger.failed(context, targetSha, message, error);
            throw new BusinessException(message + "：请核对远端的 tag 保护，再用同一版本号重试");
        }
    }

    /* ── 出参 ── */

    private Map<String, Object> result(Map<String, Object> project, Map<String, Object> page, int version,
                                       int status, String commitSha, String tagName, String branch,
                                       List<Map<String, Object>> fileRows,
                                       List<ExtensionReferenceCheck.Stale> stale, String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pageId", page.get("id"));
        out.put("pageCode", page.get("pageCode"));
        out.put("projectId", project.get("id"));
        out.put("projectCode", project.get("projectCode"));
        out.put("version", version);
        out.put("status", status);
        out.put("commitSha", commitSha);
        out.put("tagName", tagName);
        out.put("branch", branch);
        out.put("files", fileRows);
        // 体检报出来但仍被 force 放行的那些：发布成功了，但这个人有权知道自己刚才放过了什么
        out.put("warnings", stale.stream().map(hit -> Map.of(
                "path", hit.path(), "line", hit.line(), "field", hit.field())).toList());
        out.put("message", message);
        return out;
    }

    /**
     * 拿本次生成的 B 档数据类（Row / 实体 / WriteRequest）里声明的成员，去比工作区里那份 C 档扩展点。
     *
     * <p>判据用生成物而不是 {@code lc_field} 的字段清单：主键与审计列是模板补到类上的，字段表里没有它们，
     * 按字段清单判就会把 {@code row.getCreateTime()} 报成错引用。文件还不存在（首次发布）就没有可查的东西。
     */
    private List<ExtensionReferenceCheck.Stale> staleReferences(Path workspace, List<GeneratedFile> files) {
        Set<String> legal = ExtensionReferenceCheck.declaredMembers(
                files.stream().filter(this::isDataClass).map(GeneratedFile::content).toArray(String[]::new));
        List<ExtensionReferenceCheck.Stale> hits = new ArrayList<>();
        for (GeneratedFile file : files) {
            if (file.tier() != GeneratedFile.Tier.EXTENSION || !file.path().endsWith(".java")) {
                continue;
            }
            Path onDisk = workspace.resolve(file.path());
            if (!Files.isRegularFile(onDisk)) {
                continue;
            }
            try {
                hits.addAll(ExtensionReferenceCheck.scan(file.path(),
                        Files.readString(onDisk, StandardCharsets.UTF_8), Set.of(), legal));
            } catch (java.io.IOException e) {
                throw new BusinessException("读不到扩展点文件 " + file.path() + "：" + e.getMessage());
            }
        }
        return hits;
    }

    /** 生成物里「人会在扩展点上读写的对象」：{@code domain/} 下就是实体、列表行与入参三类 */
    private boolean isDataClass(GeneratedFile file) {
        return file.path().contains("/domain/") && file.path().endsWith(".java");
    }

    /** 每个产物一行：路径 + 档位 + 落盘动作 + 内容哈希。发布结果与日志里的 files_generated 都用它 */
    private List<Map<String, Object>> fileRows(List<GeneratedFile> files, List<GeneratedFileWriter.Written> writes) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (GeneratedFileWriter.Written write : writes) {
            GeneratedFile file = files.stream().filter(item -> item.path().equals(write.path())).findFirst()
                    .orElseThrow(() -> new BusinessException("产物清单与落盘结果对不上：" + write.path()));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("path", write.path());
            row.put("tier", file.tier().name());
            row.put("action", write.action().name());
            row.put("sha256", ContentHash.sha256(file.content()));
            rows.add(row);
        }
        return rows;
    }

    /** 版本行要的快照是「路径 → 内容哈希」，日志那份带落盘动作的形状对它没用处 */
    private Map<String, String> hashes(List<Map<String, Object>> fileRows) {
        Map<String, String> hashes = new LinkedHashMap<>();
        fileRows.forEach(row -> hashes.put(String.valueOf(row.get("path")), String.valueOf(row.get("sha256"))));
        return hashes;
    }

    private String commitMessage(String projectCode, Map<String, Object> page, int version) {
        return "feat(" + page.get("pageCode") + "): 发布 " + page.get("pageName") + " v" + version
                + "（元搭低代码平台，项目 " + projectCode + "）";
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException("发布结果写不成 JSON：" + e.getMessage());
        }
    }

    private static int count(List<Map<String, Object>> rows, String action) {
        return (int) rows.stream().filter(row -> action.equals(row.get("action"))).count();
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int number(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? defaultValue : Integer.parseInt(String.valueOf(value));
    }

    private static Integer integer(Object value) {
        return value == null ? null : number(value, 0);
    }

    private static long numberAsLong(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }
}

package com.scaffold.lowcode.publish;

import com.scaffold.framework.security.SecurityUtils;
import com.scaffold.lowcode.page.mapper.LcPageMapper;
import com.scaffold.lowcode.project.mapper.LcProjectMapper;
import com.scaffold.lowcode.publish.mapper.LcPageVersionMapper;
import com.scaffold.lowcode.publish.mapper.LcPublishLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 发布记账（方案 §5.1 最后两格：写 {@code lc_publish_log}；§5.4 的版本留痕）。
 *
 * <p>单独一个类而不是发布服务里的私有方法，只为让 {@code @Transactional} 真的生效：Spring 按代理织入，
 * 自调用不走代理。「版本行写成功、页面版本号没推进」这种半条命状态正好会咬人 —— 下一次发布按
 * {@code current_version} 算出同一个版本号，然后被远端那个已经存在的 tag 挡死。
 */
@Service
public class PublishLedger {

    /**
     * 一次发布动作里不随结果变化的那部分。
     *
     * @param filesJson   产物清单（路径 + 档位 + 落盘动作 + 内容哈希），进日志的 {@code files_generated}
     * @param designJson  发布那一刻的设计原文，成功时进版本行的 {@code design_snapshot}
     */
    public record Context(Long pageId, Long projectId, int version, String publishType, String branch,
                          String filesJson, String designJson, String changeDesc) {
    }

    private final LcPageMapper pageMapper;
    private final LcProjectMapper projectMapper;
    private final LcPageVersionMapper versionMapper;
    private final LcPublishLogMapper logMapper;

    public PublishLedger(LcPageMapper pageMapper, LcProjectMapper projectMapper,
                         LcPageVersionMapper versionMapper, LcPublishLogMapper logMapper) {
        this.pageMapper = pageMapper;
        this.projectMapper = projectMapper;
        this.logMapper = logMapper;
        this.versionMapper = versionMapper;
    }

    /**
     * 成功：版本行 + 日志 + 页面推到新版本 + 项目记住这个提交与发布时间，四笔一起成。
     *
     * @param snapshotJson 路径 → 内容哈希，PR 9 的文件级回滚按它判断哪个文件要取回来
     */
    @Transactional
    public void published(Context context, String commitSha, String tagName, String snapshotJson, String message) {
        Map<String, Object> version = new LinkedHashMap<>();
        version.put("pageId", context.pageId());
        version.put("version", context.version());
        version.put("designSnapshot", context.designJson());
        version.put("filesSnapshot", snapshotJson);
        version.put("commitSha", commitSha);
        version.put("changeDesc", context.changeDesc());
        versionMapper.insert(version, operator());

        insertLog(context, 1, commitSha, tagName, null);
        pageMapper.updatePublishState(context.pageId(), context.version(), 1, message, operator());
        projectMapper.markPublished(context.projectId(), commitSha, operator());
    }

    /** 无变更：不涨版本、不打 tag，但状态与时间要留 —— 人确实点过一次发布 */
    @Transactional
    public void noChange(Context context, String commitSha, String message) {
        insertLog(context, 2, commitSha, null, null);
        pageMapper.updatePublishState(context.pageId(), null, 2, message, operator());
    }

    /** 失败：不认版本、不打 tag，只把「没成」和原因写清 */
    @Transactional
    public void failed(Context context, String commitSha, String message, String error) {
        insertLog(context, 3, commitSha, null, error);
        pageMapper.updatePublishState(context.pageId(), null, 3, message, operator());
    }

    private void insertLog(Context context, int status, String commitSha, String tagName, String error) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("projectId", context.projectId());
        row.put("pageId", context.pageId());
        row.put("version", context.version());
        row.put("publishType", context.publishType());
        row.put("publishStatus", status);
        row.put("filesGenerated", context.filesJson());
        row.put("commitSha", commitSha);
        row.put("tagName", tagName);
        row.put("branch", context.branch());
        row.put("errorMessage", error);
        logMapper.insert(row, operator());
    }

    private String operator() {
        return SecurityUtils.getUsername();
    }
}

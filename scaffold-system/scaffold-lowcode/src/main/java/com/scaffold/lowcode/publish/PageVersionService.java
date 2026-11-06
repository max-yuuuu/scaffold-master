package com.scaffold.lowcode.publish;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scaffold.common.domain.PageQuery;
import com.scaffold.common.domain.PageResult;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.lowcode.generate.GeneratedFile;
import com.scaffold.lowcode.generate.GeneratedPaths;
import com.scaffold.lowcode.page.mapper.LcPageMapper;
import com.scaffold.lowcode.publish.mapper.LcPageVersionMapper;
import com.scaffold.lowcode.publish.mapper.LcPublishLogMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 版本历史（PR 9 的读那一半，方案 §5.4）。
 *
 * <p>{@code lc_page_version} 从 PR 7 起就在写，却没有一条读路径 —— 只有写没有读的表等于一叠没人翻的收据。
 * 回滚要落地，人得先看清「有哪几版、各是哪一笔提交、里面有哪些文件、哪几个文件平台有权还原」。
 *
 * <p>两条口径跟着这份读法一起定：
 * <ul>
 *   <li><b>tag 与动作类型去台账取，不抄进版本表</b>：V9 把 {@code tag_name} / {@code publish_type} 记在
 *       {@code lc_publish_log}（动作台账，一次动作一条），版本行只在成功时写。抄一份就是给同一个事实造
 *       两个真相来源 —— 台账改了，副本不会跟着改；</li>
 *   <li><b>档位现场按路径反推</b>：快照只有「路径 → 哈希」，本来就没有 tier 这一维，而
 *       {@link GeneratedPaths#tier} 是唯一的判档口径。信快照里存的档位反而多一处会漂的地方。</li>
 * </ul>
 */
@Service
public class PageVersionService {

    /**
     * 一版的取数形状。回滚只需要这些：那一笔提交、那份清单，加上当时是什么动作产出的它。
     *
     * @param files 路径 → 内容 sha256，回滚前后都拿它对账
     */
    public record VersionRow(long pageId, int version, String commitSha, String tagName, String publishType,
                             String changeDesc, Map<String, String> files) {
    }

    private final LcPageMapper pageMapper;
    private final LcPageVersionMapper versionMapper;
    private final LcPublishLogMapper logMapper;
    private final ObjectMapper objectMapper;

    public PageVersionService(LcPageMapper pageMapper, LcPageVersionMapper versionMapper,
                              LcPublishLogMapper logMapper, ObjectMapper objectMapper) {
        this.pageMapper = pageMapper;
        this.versionMapper = versionMapper;
        this.logMapper = logMapper;
        this.objectMapper = objectMapper;
    }

    /** 倒序分页：最新一版在最上面，「退回到上一版」在界面上就是第二行那个按钮 */
    public PageResult<Map<String, Object>> versions(Long pageId, PageQuery query) {
        long id = requirePage(pageId);
        long total = versionMapper.countVersions(id);
        List<Map<String, Object>> rows = total == 0 ? List.of()
                : versionMapper.selectVersions(id, query.normalizedPageSize(), query.offset());
        fillActions(id, rows);
        return PageResult.of(rows, total, query.normalizedPageNum(), query.normalizedPageSize());
    }

    /** 单版详情带文件清单：文件级回滚要在这份清单里挑，所以每个路径都得说自己归哪一档、能不能还原 */
    public Map<String, Object> detail(Long pageId, Integer version) {
        long id = requirePage(pageId);
        VersionRow row = require(id, version == null ? -1 : version);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pageId", id);
        out.put("version", row.version());
        out.put("commitSha", row.commitSha());
        out.put("tagName", row.tagName());
        out.put("publishType", row.publishType());
        out.put("changeDesc", row.changeDesc());
        out.put("files", fileRows(row.files()));
        return out;
    }

    /**
     * 取那一版，取不到就拒。
     *
     * <p>调用方是回滚这类「按版本号写仓库」的动作，所以这里不给 null 也不给空清单：
     * 拿不到那一版就没法往下写，含糊地返回空会让调用方把「一版都没有」当成「这一版没有文件」。
     */
    public VersionRow require(long pageId, int version) {
        Map<String, Object> row = versionMapper.selectVersion(pageId, version);
        if (row == null) {
            throw new BusinessException("页面没有 v" + version + "：版本历史里只记发布成功的那几版，"
                    + "先确认版本号，或重发一次再回来");
        }
        Map<String, Object> action = logMapper.selectSuccessActions(pageId, List.of(version)).stream()
                .findFirst().orElse(Map.of());
        return new VersionRow(pageId, version, text(row.get("commitSha")), text(action.get("tagName")),
                text(action.get("publishType")), text(row.get("changeDesc")), readSnapshot(row));
    }

    /** 页面归属校验：{@code selectPageBrief} 带租户条件，别的租户拿同一个 pageId 进来就是「页面不存在」 */
    private long requirePage(Long pageId) {
        if (pageId == null) {
            throw new BusinessException("页面 ID 不能为空");
        }
        if (pageMapper.selectPageBrief(pageId) == null) {
            throw new BusinessException("页面不存在");
        }
        return pageId;
    }

    private List<Map<String, Object>> fileRows(Map<String, String> snapshot) {
        List<String> paths = new ArrayList<>(snapshot.keySet());
        paths.sort(Comparator.naturalOrder());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String path : paths) {
            GeneratedFile.Tier tier = GeneratedPaths.tier(path);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("path", path);
            row.put("sha256", snapshot.get(path));
            row.put("tier", tier.name());
            // C 档不参与回滚：平台不生成它，也就无权把人的代码还原成历史里那一份（§5.4）
            row.put("rollbackable", tier == GeneratedFile.Tier.GENERATED);
            rows.add(row);
        }
        return rows;
    }

    /** 台账里那一版成功动作的 tag 与类型；查不到就留空，界面显示「—」而不是编一个 */
    private void fillActions(long pageId, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return;
        }
        List<Integer> versions = rows.stream().map(row -> (int) number(row.get("version"))).toList();
        Map<Integer, Map<String, Object>> byVersion = new HashMap<>();
        logMapper.selectSuccessActions(pageId, versions)
                .forEach(log -> byVersion.put((int) number(log.get("version")), log));
        rows.forEach(row -> {
            Map<String, Object> log = byVersion.get((int) number(row.get("version")));
            row.put("tagName", log == null ? null : log.get("tagName"));
            row.put("publishType", log == null ? null : log.get("publishType"));
        });
    }

    private Map<String, String> readSnapshot(Map<String, Object> row) {
        Object raw = row.get("filesSnapshot");
        if (raw == null) {
            throw new BusinessException("v" + row.get("version") + " 的版本快照是空的：那一版没记下任何产物，"
                    + "回滚无从取数");
        }
        try {
            return objectMapper.readValue(String.valueOf(raw), new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (Exception e) {
            throw new BusinessException("v" + row.get("version") + " 的版本快照读不出文件清单：" + e.getMessage());
        }
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}

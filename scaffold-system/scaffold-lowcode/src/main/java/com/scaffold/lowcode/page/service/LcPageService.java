package com.scaffold.lowcode.page.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scaffold.common.domain.PageQuery;
import com.scaffold.common.domain.PageResult;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.framework.security.SecurityUtils;
import com.scaffold.lowcode.design.DesignContext;
import com.scaffold.lowcode.design.PageDesign;
import com.scaffold.lowcode.model.mapper.LcFieldMapper;
import com.scaffold.lowcode.model.mapper.LcModelMapper;
import com.scaffold.lowcode.page.PageReferences;
import com.scaffold.lowcode.page.domain.PageWriteRequest;
import com.scaffold.lowcode.page.mapper.LcPageMapper;
import com.scaffold.lowcode.project.mapper.LcProjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 页面设计（lc_page）。页面是「形」、模型是「料」，这一层的职责就是在保存时把两者对一遍（方案 §1.5）。
 *
 * <p>为什么拦在保存而不是发布：设计器给人自由摆，生成器只认模型与模板。一份绑了不存在字段的设计
 * 放过之后，要到 PR 7 发布、再到交付项目构建才暴露 —— 5G 演练里那六条接缝缺陷全是这个位置漏出去的。
 * 库里因此只留「一定生成得出来」的设计，代价是改一次模型字段要重新保存受影响的页面，
 * 而这个由 {@code ref_fields} 反查在删字段时就先问一遍。
 */
@Service
public class LcPageService {

    private final LcPageMapper pageMapper;
    private final LcModelMapper modelMapper;
    private final LcFieldMapper fieldMapper;
    private final LcProjectMapper projectMapper;
    private final ObjectMapper objectMapper;

    public LcPageService(LcPageMapper pageMapper, LcModelMapper modelMapper, LcFieldMapper fieldMapper,
                         LcProjectMapper projectMapper, ObjectMapper objectMapper) {
        this.pageMapper = pageMapper;
        this.modelMapper = modelMapper;
        this.fieldMapper = fieldMapper;
        this.projectMapper = projectMapper;
        this.objectMapper = objectMapper;
    }

    public PageResult<Map<String, Object>> pages(PageQuery query, Long projectId, Long modelId, String keyword, Integer status) {
        String key = trimToNull(keyword);
        long total = pageMapper.countPages(projectId, modelId, key, status);
        List<Map<String, Object>> rows = total == 0
                ? List.of()
                : pageMapper.selectPages(projectId, modelId, key, status, query.offset(), query.normalizedPageSize());
        return PageResult.of(rows, total, query.normalizedPageNum(), query.normalizedPageSize());
    }

    /** 详情带设计原文：设计器打开页面吃的就是这一份 */
    public Map<String, Object> detail(Long id) {
        Map<String, Object> row = pageMapper.selectPageBrief(id);
        if (row == null) {
            throw new BusinessException("页面不存在");
        }
        return withParsed(row);
    }

    @Transactional
    public Map<String, Object> create(PageWriteRequest request) {
        Long projectId = request.getProjectId();
        if (projectId == null || projectMapper.selectProjectBrief(projectId) == null) {
            throw new BusinessException("项目不存在或不属于当前租户");
        }
        String pageCode = required(request.getPageCode(), "页面编码不能为空");
        if (pageMapper.countPageCode(projectId, pageCode, null) > 0) {
            throw new BusinessException("该项目下已存在同名页面编码");
        }
        Map<String, Object> model = requireModel(projectId, request.getModelCode());
        PageDesign design = parseDesign(model, request.getDesign());
        requireSingleListPage(model, null);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("projectId", projectId);
        row.put("modelId", model.get("id"));
        row.put("pageCode", pageCode);
        row.put("pageName", required(request.getPageName(), "页面名称不能为空"));
        row.put("pageDesc", trimToNull(request.getPageDesc()));
        // page_type 是设计里 pageType 的副本，只为列表筛选与展示；写第二份事实的入口只有这一处
        row.put("pageType", design.pageType());
        row.put("routePath", trimToNull(request.getRoutePath()));
        row.put("menuIcon", trimToNull(request.getMenuIcon()));
        row.put("menuSort", request.getMenuSort());
        row.put("designJson", request.getDesign().toString());
        row.put("refFields", writeJson(design.referencedFields()));
        row.put("status", statusOf(request.getStatus(), 1));
        pageMapper.insertPage(row, operator());
        return Map.of("id", row.get("id"));
    }

    @Transactional
    public void update(Long id, PageWriteRequest request) {
        Map<String, Object> existing = detail(id);
        // 这三样决定生成物的落点：换项目是换仓库、换模型是换一批字段、换编码是换文件名，
        // 已发布的页面历史会被挪到另一条链路上，所以创建后一律不给改
        if (request.getProjectId() != null && !request.getProjectId().equals(numberAsLong(existing.get("projectId")))) {
            throw new BusinessException("页面归属项目不可修改");
        }
        if (trimToNull(request.getModelCode()) != null
                && !request.getModelCode().trim().equals(String.valueOf(existing.get("modelCode")))) {
            throw new BusinessException("页面归属模型不可修改，请新建页面");
        }
        if (trimToNull(request.getPageCode()) != null
                && !request.getPageCode().trim().equals(String.valueOf(existing.get("pageCode")))) {
            throw new BusinessException("页面编码不可修改，请新建页面");
        }
        Map<String, Object> model = modelMapper.selectModelBrief(numberAsLong(existing.get("modelId")));
        if (model == null) {
            throw new BusinessException("页面绑定的模型不存在，请重新绑定模型");
        }
        // 每次保存都重新对一遍设计：字段可能在两次保存之间被删掉，旧设计指着它就存不进去了
        PageDesign design = parseDesign(model, request.getDesign());
        requireSingleListPage(model, id);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("pageName", required(request.getPageName(), "页面名称不能为空"));
        row.put("pageDesc", trimToNull(request.getPageDesc()));
        row.put("pageType", design.pageType());
        row.put("routePath", trimToNull(request.getRoutePath()));
        row.put("menuIcon", trimToNull(request.getMenuIcon()));
        row.put("menuSort", request.getMenuSort());
        row.put("designJson", request.getDesign().toString());
        row.put("refFields", writeJson(design.referencedFields()));
        row.put("status", request.getStatus() == null ? null : statusOf(request.getStatus(), 1));
        if (pageMapper.updatePage(id, row, operator()) == 0) {
            throw new BusinessException("页面不存在或已删除");
        }
    }

    @Transactional
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态只能为 1 正常或 0 停用");
        }
        detail(id);
        if (pageMapper.updatePageStatus(id, status, operator()) == 0) {
            throw new BusinessException("页面不存在或已删除");
        }
    }

    /** 软删：页面可能已经发布进过项目仓库，删掉要留痕，反查也不再算它 */
    @Transactional
    public void delete(Long id) {
        detail(id);
        if (pageMapper.softDeletePage(id, operator()) == 0) {
            throw new BusinessException("页面不存在或已删除");
        }
    }

    /**
     * 项目下还有页面就不许删项目。
     *
     * @return 未删除的页面数
     */
    public long countPagesOfProject(Long projectId) {
        return pageMapper.countPagesOfProject(projectId);
    }

    /* ── 内部 ── */

    /**
     * 一个模型第一期只允许一个列表页。
     *
     * <p>落点由模型类名派生，放两个进来等于让后一次发布整文件盖掉前一次的产物 —— 设计器里看着是
     * 两份设计都能生成，交付项目里只剩一个文件，这种「看着都对」的覆盖最难查。要另一份视图请先做
     * 只读台账（另建一个列表页得换模型）或等详情页模板进生成器。
     */
    private void requireSingleListPage(Map<String, Object> model, Long excludeId) {
        List<Map<String, Object>> existing = pageMapper.selectListPagesOfModel(numberAsLong(model.get("id")), excludeId);
        if (existing.isEmpty()) {
            return;
        }
        String codes = existing.stream().map(page -> String.valueOf(page.get("pageCode"))).toList().toString();
        throw new BusinessException("模型「" + model.get("modelCode") + "」已经有一个列表页 " + codes
                + "：一个模型第一期只允许一个列表页，页面落点由模型类名派生，第二个会在下次发布整文件盖掉第一个");
    }

    private Map<String, Object> requireModel(Long projectId, String modelCode) {
        String code = required(modelCode, "页面必须绑定一个模型");
        Map<String, Object> model = modelMapper.selectModelBriefByCode(projectId, code);
        if (model == null) {
            throw new BusinessException("模型不存在或不属于当前项目：" + code);
        }
        return model;
    }

    /** 设计与模型逐字段对一遍，规则见 {@link PageDesign} */
    private PageDesign parseDesign(Map<String, Object> model, JsonNode design) {
        if (design == null || design.isNull() || design.isEmpty()) {
            throw new BusinessException("页面设计不能为空：至少要声明 layout 与一个 DataTable");
        }
        long modelId = numberAsLong(model.get("id"));
        DesignContext context = DesignContext.of(String.valueOf(model.get("modelCode")), fieldMapper.selectByModel(modelId));
        return PageDesign.parse(design.toString(), context);
    }

    private Map<String, Object> withParsed(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row);
        Object design = out.get("design");
        if (design != null) {
            try {
                out.put("design", objectMapper.readTree(String.valueOf(design)));
            } catch (Exception ex) {
                throw new BusinessException("页面设计读不出来，这份记录已不可用：" + ex.getMessage());
            }
        }
        out.put("refFields", PageReferences.fieldsOf(out.get("refFields")));
        return out;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException("页面引用清单写不出来：" + ex.getMessage());
        }
    }

    private static int statusOf(Integer value, int defaultValue) {
        return value == null ? defaultValue : (value == 0 ? 0 : 1);
    }

    private String operator() {
        return SecurityUtils.getUsername();
    }

    private static String required(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BusinessException(message);
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static long numberAsLong(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }
}

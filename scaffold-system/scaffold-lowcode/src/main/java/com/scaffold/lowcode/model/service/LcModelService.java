package com.scaffold.lowcode.model.service;

import com.scaffold.common.domain.PageQuery;
import com.scaffold.common.domain.PageResult;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.framework.security.SecurityUtils;
import com.scaffold.lowcode.model.Naming;
import com.scaffold.lowcode.model.domain.ModelWriteRequest;
import com.scaffold.lowcode.model.mapper.LcFieldMapper;
import com.scaffold.lowcode.model.mapper.LcModelMapper;
import com.scaffold.lowcode.project.mapper.LcProjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型管理。模型是生成器的输入（方案 §1.4）：它不产生任何运行时行为，
 * 只描述「这个项目里应该有一张什么样的表、一套什么样的接口」。
 */
@Service
public class LcModelService {

    private final LcModelMapper modelMapper;
    private final LcFieldMapper fieldMapper;
    private final LcProjectMapper projectMapper;

    public LcModelService(LcModelMapper modelMapper, LcFieldMapper fieldMapper, LcProjectMapper projectMapper) {
        this.modelMapper = modelMapper;
        this.fieldMapper = fieldMapper;
        this.projectMapper = projectMapper;
    }

    public PageResult<Map<String, Object>> models(PageQuery query, Long projectId, String keyword, Integer status) {
        String key = trimToNull(keyword);
        long total = modelMapper.countModels(projectId, key, status);
        List<Map<String, Object>> rows = total == 0
                ? List.of()
                : modelMapper.selectModels(projectId, key, status, query.offset(), query.normalizedPageSize());
        return PageResult.of(rows, total, query.normalizedPageNum(), query.normalizedPageSize());
    }

    /** REF 字段选目标模型用的精简列表 */
    public List<Map<String, Object>> options(Long projectId) {
        return modelMapper.selectModelOptions(projectId);
    }

    public Map<String, Object> detail(Long id) {
        Map<String, Object> model = modelMapper.selectModelBrief(id);
        if (model == null) {
            throw new BusinessException("模型不存在");
        }
        return model;
    }

    @Transactional
    public Map<String, Object> create(ModelWriteRequest request) {
        Long projectId = request.getProjectId();
        if (projectId == null || projectMapper.selectProjectBrief(projectId) == null) {
            throw new BusinessException("请先选择归属项目");
        }
        String modelCode = required(request.getModelCode(), "模型编码不能为空");
        if (modelMapper.countModelCode(projectId, modelCode, null) > 0) {
            throw new BusinessException("该项目下已存在同名模型编码");
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("projectId", projectId);
        row.put("modelCode", modelCode);
        row.put("modelName", required(request.getModelName(), "模型名称不能为空"));
        row.put("modelDesc", trimToNull(request.getModelDesc()));
        row.put("tableName", defaultText(request.getTableName(), Naming.snake(modelCode)));
        row.put("className", defaultText(request.getClassName(), Naming.pascal(modelCode)));
        row.put("moduleName", defaultText(request.getModuleName(), Naming.packageSegment(modelCode)));
        row.put("isTree", flag(request.getIsTree(), 0));
        row.put("enableLogicDelete", flag(request.getEnableLogicDelete(), 1));
        row.put("enableDataScope", flag(request.getEnableDataScope(), 0));
        row.put("enableAuditField", flag(request.getEnableAuditField(), 1));
        row.put("status", flag(request.getStatus(), 1));
        requireUniqueTable(projectId, row, null);
        modelMapper.insertModel(row, operator());
        return Map.of("id", row.get("id"));
    }

    @Transactional
    public void update(Long id, ModelWriteRequest request) {
        Map<String, Object> existing = detail(id);
        // 归属项目与模型编码创建后不可改：它们决定生成物的落点，改了等于换了一张表的历史
        if (request.getProjectId() != null && !request.getProjectId().equals(numberAsLong(existing.get("projectId")))) {
            throw new BusinessException("模型归属项目不可修改");
        }
        if (trimToNull(request.getModelCode()) != null
                && !request.getModelCode().trim().equals(String.valueOf(existing.get("modelCode")))) {
            throw new BusinessException("模型编码不可修改，请新建模型");
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("modelName", trimToNull(request.getModelName()));
        row.put("modelDesc", trimToNull(request.getModelDesc()));
        row.put("tableName", trimToNull(request.getTableName()));
        row.put("className", trimToNull(request.getClassName()));
        row.put("moduleName", trimToNull(request.getModuleName()));
        row.put("isTree", request.getIsTree());
        row.put("enableLogicDelete", request.getEnableLogicDelete());
        row.put("enableDataScope", request.getEnableDataScope());
        row.put("enableAuditField", request.getEnableAuditField());
        row.put("status", request.getStatus());
        requireUniqueTable(numberAsLong(existing.get("projectId")), row, id);
        if (modelMapper.updateModel(id, row, operator()) == 0) {
            throw new BusinessException("模型不存在或已删除");
        }
    }

    @Transactional
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态只能为 1 正常或 0 停用");
        }
        detail(id);
        if (modelMapper.updateModelStatus(id, status, operator()) == 0) {
            throw new BusinessException("模型不存在或已删除");
        }
    }

    /**
     * 删除模型：软删模型，字段直接删掉（字段是模型的内部结构，不是独立资产）。
     * PR 6 有了 lc_page.ref_fields 之后，这里还要拦「仍有页面引用该模型的字段」。
     */
    @Transactional
    public void delete(Long id) {
        detail(id);
        modelMapper.deleteFieldsOfModel(id);
        if (modelMapper.softDeleteModel(id, operator()) == 0) {
            throw new BusinessException("模型不存在或已删除");
        }
    }

    private void requireUniqueTable(long projectId, Map<String, Object> row, Long excludeId) {
        Object tableName = row.get("tableName");
        if (tableName == null) {
            return;
        }
        if (modelMapper.countModelTable(projectId, String.valueOf(tableName), excludeId) > 0) {
            throw new BusinessException("该项目下已存在同名数据表");
        }
    }

    private static String defaultText(String value, String defaultValue) {
        String trimmed = trimToNull(value);
        return trimmed == null ? defaultValue : trimmed;
    }

    private static int flag(Integer value, int defaultValue) {
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

package com.scaffold.lowcode.model.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.framework.security.SecurityUtils;
import com.scaffold.lowcode.model.FieldType;
import com.scaffold.lowcode.model.FormType;
import com.scaffold.lowcode.model.Naming;
import com.scaffold.lowcode.model.QueryType;
import com.scaffold.lowcode.model.SqlIdentifiers;
import com.scaffold.lowcode.model.domain.FieldWriteRequest;
import com.scaffold.lowcode.model.mapper.LcFieldMapper;
import com.scaffold.lowcode.model.mapper.LcModelMapper;
import com.scaffold.lowcode.page.PageReferences;
import com.scaffold.lowcode.page.mapper.LcPageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 字段配置。字段的 Java 类型与列类型<b>不在这里拍脑袋</b>，一律由 {@link FieldType} 推导后落库，
 * 模板生成时直接读库里的结果（方案 §1.4：模型是生成器的输入，输入必须先自洽）。
 */
@Service
public class LcFieldService {

    private final LcFieldMapper fieldMapper;
    private final LcModelMapper modelMapper;
    private final LcPageMapper pageMapper;
    private final ObjectMapper objectMapper;

    public LcFieldService(LcFieldMapper fieldMapper, LcModelMapper modelMapper, LcPageMapper pageMapper,
                          ObjectMapper objectMapper) {
        this.fieldMapper = fieldMapper;
        this.modelMapper = modelMapper;
        this.pageMapper = pageMapper;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> fields(Long modelId) {
        requireModel(modelId);
        return fieldMapper.selectByModel(modelId).stream().map(this::withParsedOptions).toList();
    }

    /** REF 字段「下拉显示哪一列」的候选 */
    public List<Map<String, Object>> fieldOptions(Long modelId) {
        requireModel(modelId);
        return fieldMapper.selectFieldOptions(modelId);
    }

    @Transactional
    public Map<String, Object> create(Long modelId, FieldWriteRequest request) {
        Map<String, Object> model = requireModel(modelId);
        String fieldCode = required(request.getFieldCode(), "字段编码不能为空");
        if (fieldMapper.countFieldCode(modelId, fieldCode, null) > 0) {
            throw new BusinessException("该模型下已存在同名字段编码");
        }
        Map<String, Object> row = buildRow(model, request, null);
        row.put("fieldCode", fieldCode);
        row.put("sortOrder", request.getSortOrder() == null ? fieldMapper.countFields(modelId) + 1 : request.getSortOrder());
        row.put("modelId", modelId);
        fieldMapper.insertField(row, operator());
        return Map.of("id", row.get("id"));
    }

    @Transactional
    public void update(Long id, FieldWriteRequest request) {
        Map<String, Object> existing = requireField(id);
        long modelId = numberAsLong(existing.get("modelId"));
        Map<String, Object> model = requireModel(modelId);
        String fieldCode = trimToNull(request.getFieldCode());
        if (fieldCode != null && fieldMapper.countFieldCode(modelId, fieldCode, id) > 0) {
            throw new BusinessException("该模型下已存在同名字段编码");
        }
        Map<String, Object> row = buildRow(model, request, id);
        row.put("fieldCode", fieldCode);
        if (fieldMapper.updateField(id, row, operator()) == 0) {
            throw new BusinessException("字段不存在");
        }
    }

    @Transactional
    public void delete(Long id) {
        Map<String, Object> field = requireField(id);
        long modelId = numberAsLong(field.get("modelId"));
        // 删字段会打断正在用它的页面，所以入口就拦（方案 §1.5）：按 lc_page.ref_fields 反查，
        // 而不是等发布时模板报错才发现 —— 那时人已经在跟交付项目的构建日志较劲了
        List<Map<String, Object>> hit = PageReferences.using(pageMapper.selectPagesOfModel(modelId),
                String.valueOf(field.get("fieldCode")));
        if (!hit.isEmpty()) {
            throw new BusinessException("字段 " + field.get("fieldCode") + " 还被 " + hit.size()
                    + " 个页面在用：" + PageReferences.names(hit)
                    + "。请先在设计里删掉这一格或换绑别的字段，再来删字段");
        }
        fieldMapper.deleteField(id);
    }

    /**
     * 把请求换算成库里的一行：类型解析、约束校验、列类型与 Java 类型推导都在这里完成。
     *
     * @param excludeId 编辑时排除自身，用于主键与编码查重
     */
    private Map<String, Object> buildRow(Map<String, Object> model, FieldWriteRequest request, Long excludeId) {
        FieldType type;
        try {
            type = FieldType.of(request.getFieldType());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ex.getMessage());
        }
        long modelId = numberAsLong(model.get("id"));
        boolean primary = isOn(request.getIsPrimary());
        boolean autoIncrement = isOn(request.getIsAutoIncrement());
        if (autoIncrement && !primary) {
            throw new BusinessException("自增字段必须是主键");
        }
        if (primary) {
            if (fieldMapper.countPrimaryKeys(modelId, excludeId) > 0) {
                throw new BusinessException("一个模型只能有一个主键字段");
            }
            if (type == FieldType.TEXT || type == FieldType.DATETIME || type == FieldType.REF) {
                throw new BusinessException("主键字段只能是编码、整数、小数或日期类型");
            }
        }
        if (type.requiresReference()) {
            requireReferenceTarget(model, request);
        } else if (request.getRefModelId() != null || request.getRefFieldId() != null) {
            throw new BusinessException("只有「关联引用」类型才能指向其他模型");
        }
        String dictType = trimToNull(request.getDictType());
        if (type.requiresDict() && dictType == null) {
            throw new BusinessException("字典枚举类型必须选择字典");
        }
        if (dictType != null && fieldMapper.countDictType(dictType) == 0) {
            throw new BusinessException("字典类型在当前租户不存在: " + dictType);
        }

        FormType formType = FormType.of(request.getFormType(), type.defaultFormType());
        String formOptionsJson = writeOptions(request.getFormOptions());
        if ((formType == FormType.RADIO || formType == FormType.CHECKBOX) && formOptionsJson == null && dictType == null) {
            throw new BusinessException("单选 / 多选控件要么挂字典，要么给出固定选项");
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("fieldType", type.code());
        row.put("javaType", type.javaType(autoIncrement));
        // 列名只由字段编码派生，不给单独改列名的入口：改了列名，历史数据与生成物就对不上了
        String columnName = request.getFieldCode() == null ? null : Naming.snake(request.getFieldCode());
        // 生成物的 SQL 不给列名加反引号（加了每行都读不动），所以保留字在这里就拦住
        SqlIdentifiers.requireColumnName(columnName);
        row.put("columnName", columnName);
        row.put("columnLength", type == FieldType.DECIMAL ? null : request.getColumnLength());
        row.put("columnPrecision", type == FieldType.DECIMAL ? request.getColumnPrecision() : null);
        row.put("columnScale", type == FieldType.DECIMAL ? request.getColumnScale() : null);
        row.put("columnType", type.columnType(request.getColumnLength(), request.getColumnPrecision(), request.getColumnScale(), autoIncrement));
        row.put("fieldName", required(request.getFieldName(), "字段名称不能为空"));
        row.put("fieldDesc", trimToNull(request.getFieldDesc()));
        row.put("isPrimary", primary ? 1 : 0);
        row.put("isAutoIncrement", autoIncrement ? 1 : 0);
        // 主键不可空；其余按填写
        row.put("isNullable", primary ? 0 : (isOnExplicit(request.getIsNullable(), 1) ? 1 : 0));
        row.put("isUnique", isOn(request.getIsUnique()) ? 1 : 0);
        row.put("defaultValue", trimToNull(request.getDefaultValue()));
        row.put("isList", isOnExplicit(request.getIsList(), 1) ? 1 : 0);
        row.put("isQuery", isOn(request.getIsQuery()) ? 1 : 0);
        row.put("queryType", QueryType.of(request.getQueryType()).code());
        // 自增主键由数据库给值，「可录入 / 可编辑 / 必填」这三格对它没有意义：
        // 留着就会生成一张要人填主键的新增表单，字段接口给的默认值又都是开着的，所以在这里压掉。
        // 不自增的主键（业务编号当主键）不压 —— 那一列真的需要人填
        boolean autoKey = primary && autoIncrement;
        row.put("isInsert", autoKey ? 0 : (isOnExplicit(request.getIsInsert(), 1) ? 1 : 0));
        row.put("isEdit", autoKey ? 0 : (isOnExplicit(request.getIsEdit(), 1) ? 1 : 0));
        row.put("isRequired", autoKey ? 0 : ((primary || isOn(request.getIsRequired())) ? 1 : 0));
        row.put("formType", formType.name());
        row.put("formOptions", formOptionsJson);
        row.put("dictType", dictType);
        row.put("refModelId", type.requiresReference() ? request.getRefModelId() : null);
        row.put("refFieldId", type.requiresReference() ? request.getRefFieldId() : null);
        row.put("sortOrder", request.getSortOrder());
        return row;
    }

    /** REF 目标必须是同一项目、同一租户下的模型；指向自身是合法的（树表的 parent_id 就是这样） */
    private void requireReferenceTarget(Map<String, Object> model, FieldWriteRequest request) {
        Long refModelId = request.getRefModelId();
        if (refModelId == null) {
            throw new BusinessException("关联引用必须选择目标模型");
        }
        Map<String, Object> target = modelMapper.selectModelBrief(refModelId);
        if (target == null) {
            throw new BusinessException("关联的目标模型不存在");
        }
        if (numberAsLong(target.get("projectId")) != numberAsLong(model.get("projectId"))) {
            throw new BusinessException("关联目标模型必须与当前模型属于同一个项目");
        }
        Long refFieldId = request.getRefFieldId();
        if (refFieldId != null) {
            Map<String, Object> field = fieldMapper.selectFieldBrief(refFieldId);
            if (field == null || numberAsLong(field.get("modelId")) != refModelId) {
                throw new BusinessException("关联展示字段不属于目标模型");
            }
        }
    }

    private String writeOptions(List<FieldWriteRequest.FormOption> options) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> cleaned = new ArrayList<>();
        for (FieldWriteRequest.FormOption option : options) {
            String label = option == null ? null : trimToNull(option.label());
            String value = option == null ? null : trimToNull(option.value());
            if (label == null || value == null) {
                throw new BusinessException("选项的名称与取值都不能为空");
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("label", label);
            item.put("value", value);
            cleaned.add(item);
        }
        try {
            return objectMapper.writeValueAsString(cleaned);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("选项序列化失败");
        }
    }

    private Map<String, Object> withParsedOptions(Map<String, Object> row) {
        Map<String, Object> copy = new LinkedHashMap<>(row);
        Object raw = copy.get("formOptions");
        copy.put("formOptions", raw == null ? List.of() : readOptions(String.valueOf(raw)));
        return copy;
    }

    private List<Map<String, Object>> readOptions(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (JsonProcessingException ex) {
            // 库里存的选项坏了不能挡住整个字段列表，回空并在日志外静默
            return List.of();
        }
    }

    private Map<String, Object> requireModel(Long modelId) {
        Map<String, Object> model = modelMapper.selectModelBrief(modelId);
        if (model == null) {
            throw new BusinessException("模型不存在");
        }
        return model;
    }

    private Map<String, Object> requireField(Long id) {
        Map<String, Object> field = fieldMapper.selectFieldBrief(id);
        if (field == null) {
            throw new BusinessException("字段不存在");
        }
        return field;
    }

    private static boolean isOn(Integer value) {
        return value != null && value != 0;
    }

    /** 未传时按默认值（编辑态 COALESCE 会保持原值，这里只处理新增与显式修改） */
    private static boolean isOnExplicit(Integer value, int defaultValue) {
        return value == null ? defaultValue == 1 : value != 0;
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

<#import "../_shared/methodParams.ftl" as mp>
package com.scaffold.business.${module}.service;

import com.scaffold.business.${module}.domain.${className};
import com.scaffold.business.${module}.domain.${className}Row;
import com.scaffold.business.${module}.domain.${className}WriteRequest;
import com.scaffold.business.${module}.mapper.${className}Mapper;
import com.scaffold.common.domain.PageQuery;
import com.scaffold.common.domain.PageResult;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.common.export.CsvUtils;
import com.scaffold.framework.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

<#list queryParamImports as type>
import ${type};
</#list>
import java.util.List;

/**
 * ${modelDesc}（表 {@code ${tableName}}）的业务入口。
 *
 * <p>本类由元搭低代码平台按模型定义生成，属 B 档：每次发布整文件覆盖。业务规则、状态流转、
 * 跨实体事务请写到同包的 {@code ${className}Extension}（C 档，生成一次后平台不再覆盖）。
 *
 * <p>单类 Service，不做 interface + impl 的二次分层 —— 与底座 {@code SystemManagementService}
 * 同一口径，一个模型一个类，读的人不用在两个文件之间跳。
 */
@Service
public class ${className}Service {

    /** 导出行数上限：导出不该把整表拉进内存，要全量请走数据库导出 */
    private static final int EXPORT_LIMIT = 5000;

    private final ${className}Mapper mapper;

    public ${className}Service(${className}Mapper mapper) {
        this.mapper = mapper;
    }

    /** 分页列表。查询条件逐个平铺在签名上：前端传什么、SQL 查什么，不用翻到 Mapper 才看清楚 */
    public PageResult<${className}Row> page(
            PageQuery query<#if queryParams?size gt 0>,</#if>
<#list queryParams as p>
            ${p.javaType} ${p.name}<#if p?has_next>,</#if>
</#list>
    ) {
        List<${className}Row> rows = mapper.selectRows(<@mp.call items=queryParams /><#if queryParams?size gt 0>, </#if>query.offset(), query.normalizedPageSize());
        long total = mapper.countByQuery(<@mp.call items=queryParams />);
        return PageResult.of(rows, total, query.normalizedPageNum(), query.normalizedPageSize());
    }

    /**
     * 导出 CSV，条件与列表同一套。<b>字典与关联列导出的是取值</b>（字典 code、目标行主键），
     * 不是中文标签 —— 需要中文时在 C 档里换，别在导出的时候顺手改口径。
     */
    public String export(<#if queryParams?size gt 0>
<#list queryParams as p>
            ${p.javaType} ${p.name}<#if p?has_next>,</#if>
</#list>
    </#if>) {
        List<${className}Row> rows = mapper.selectRows(<@mp.call items=queryParams /><#if queryParams?size gt 0>, </#if>0, EXPORT_LIMIT);
        List<List<Object>> data = rows.stream()
                .map(row -> CsvUtils.row(<#list rowFields as f>row.get${f.javaField?cap_first}()<#if f?has_next>, </#if></#list>))
                .toList();
        return CsvUtils.csv(List.of(<#list rowFields as f>"${f.desc}"<#if f?has_next>, </#if></#list>), data);
    }

    /** 新增${modelDesc}，返回新行主键 */
    @Transactional
    public Long create(${className}WriteRequest request) {
<#list insertFields as f>
<#if f.required>
        if (request.get${f.javaField?cap_first}() == null<#if f.stringType> || request.get${f.javaField?cap_first}().isBlank()</#if>) {
            throw new BusinessException("${f.desc}不能为空");
        }
</#if>
</#list>
<#list uniqueFields as f>
        if (mapper.countBy${f.javaField?cap_first}(request.get${f.javaField?cap_first}(), null) > 0) {
            throw new BusinessException("${f.desc}已存在");
        }
</#list>
        ${className} entity = new ${className}();
<#list insertFields as f>
        entity.set${f.javaField?cap_first}(request.get${f.javaField?cap_first}());
</#list>
        mapper.insert(entity, SecurityUtils.getUsername());
        return entity.get${primaryField.javaField?cap_first}();
    }

    /**
     * 修改${modelDesc}。表单提交的是整个表单，勾选了「可编辑」的字段按新值覆盖，
     * 没勾选的（含主键与审计列）一个都不碰。
     */
    @Transactional
    public void update(Long id, ${className}WriteRequest request) {
<#list updateFields as f>
<#if f.required>
        if (request.get${f.javaField?cap_first}() == null<#if f.stringType> || request.get${f.javaField?cap_first}().isBlank()</#if>) {
            throw new BusinessException("${f.desc}不能为空");
        }
</#if>
</#list>
<#list uniqueFields as f>
        if (mapper.countBy${f.javaField?cap_first}(request.get${f.javaField?cap_first}(), id) > 0) {
            throw new BusinessException("${f.desc}已存在");
        }
</#list>
        ${className} entity = new ${className}();
<#list updateFields as f>
        entity.set${f.javaField?cap_first}(request.get${f.javaField?cap_first}());
</#list>
        if (mapper.update(id, entity, SecurityUtils.getUsername()) == 0) {
            throw new BusinessException("${modelName}不存在或已删除");
        }
    }

    /**
     * 删除${modelDesc}。<#if logicDelete>这张表勾了「逻辑删除」：只翻删除标记，历史留在库里。
     * <#else>这张表没勾「逻辑删除」：删掉就是真删，历史不可回溯 —— 要留痕请在建模时勾上逻辑删除。
     * </#if>受影响行数为 0 说明这一行本来就不在了。
     */
    @Transactional
    public void delete(Long id) {
        if (mapper.deleteById(id<#if logicDelete && audit>, SecurityUtils.getUsername()</#if>) == 0) {
            throw new BusinessException("${modelName}不存在或已删除");
        }
    }
}

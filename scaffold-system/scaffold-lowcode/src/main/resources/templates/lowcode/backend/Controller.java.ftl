<#import "../_shared/methodParams.ftl" as mp>
package com.scaffold.business.${module}.controller;

import com.scaffold.business.${module}.domain.${className};
import com.scaffold.business.${module}.domain.${className}Row;
import com.scaffold.business.${module}.domain.${className}WriteRequest;
import com.scaffold.business.${module}.service.${className}Service;
import com.scaffold.common.domain.PageQuery;
import com.scaffold.common.domain.PageResult;
import com.scaffold.common.domain.R;
import com.scaffold.framework.audit.OperLog;
import com.scaffold.framework.security.RequiresPermission;
import com.scaffold.framework.web.CsvResponse;<#if hasDateQuery>
import org.springframework.format.annotation.DateTimeFormat;</#if>
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
<#if queryParamImports?size gt 0>

<#list queryParamImports as type>
import ${type};
</#list>
</#if>

/**
 * ${modelDesc}（表 {@code ${tableName}}）的接口。
 *
 * <p>本类由元搭低代码平台按模型定义生成，属 B 档：每次发布整文件覆盖，要加接口请回到设计器，
 * 或在同包的 {@code ${className}Extension}（C 档）里做，别在这里塞业务分支。
 *
 * <p>五个入口固定成一套（列表 / 导出 / 新增 / 修改 / 删除），路径与权限点由模型编码派生；
 * 返回结构统一走 {@code R}，分页统一走 {@code PageResult}，与底座 {@code /api/system} 同一形状。
 * 自定义动作在设计器里选 {@code custom:{name}}，平台只出一个带 TODO 的桩，实现由人填。
 */
@RestController
@RequestMapping("/api${apiPath}")
public class ${className}Controller {

    private final ${className}Service service;

    public ${className}Controller(${className}Service service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermission("${permissionPrefix}:list")
    public R<PageResult<${className}Row>> list(
            PageQuery query<#if queryParams?size gt 0>,</#if>
<#list queryParams as p>
            @RequestParam(required = false)<#if p.jsonPattern??> @DateTimeFormat(pattern = "${p.jsonPattern}")</#if> ${p.javaType} ${p.name}<#if p?has_next>,</#if>
</#list>
    ) {
        return R.success(service.page(query<@mp.call items=queryParams prefix=", " />));
    }

    /** 单行详情：前端编辑前取整行，避免用列表行填表时把没显示的字段清空 */
    @GetMapping("/{id}")
    @RequiresPermission("${permissionPrefix}:list")
    public R<${className}> detail(@PathVariable Long id) {
        return R.success(service.detail(id));
    }

    /** 导出当前筛选条件下的 CSV（上限由 Service 把住），文件名用模型中文名 */
    @GetMapping("/export")
    @RequiresPermission("${permissionPrefix}:list")
    public ResponseEntity<String> export(<#if queryParams?size gt 0>
<#list queryParams as p>
            @RequestParam(required = false)<#if p.jsonPattern??> @DateTimeFormat(pattern = "${p.jsonPattern}")</#if> ${p.javaType} ${p.name}<#if p?has_next>,</#if>
</#list>
    </#if>) {
        return CsvResponse.attachment("${resourceName}.csv", service.export(<@mp.call items=queryParams />));
    }

    @PostMapping
    @RequiresPermission("${permissionPrefix}:add")
    @OperLog(module = "${modelDesc}", action = "新增")
    public R<Long> create(@RequestBody ${className}WriteRequest request) {
        return R.success(service.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission("${permissionPrefix}:edit")
    @OperLog(module = "${modelDesc}", action = "修改")
    public R<Void> update(@PathVariable Long id, @RequestBody ${className}WriteRequest request) {
        service.update(id, request);
        return R.success(null);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("${permissionPrefix}:remove")
    @OperLog(module = "${modelDesc}", action = "删除")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.success(null);
    }
}

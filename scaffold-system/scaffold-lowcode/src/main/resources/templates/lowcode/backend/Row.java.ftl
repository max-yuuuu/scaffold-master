<#import "../_shared/auditFields.ftl" as auditFields>
<#import "../_shared/pojo.ftl" as pojo>
package com.scaffold.business.${module}.domain;

<#list rowImports as type>
import ${type};
</#list>

/**
 * ${modelDesc}的列表行（表 {@code ${tableName}}）。
 *
 * <p>本类由低代码设计器按模型定义生成，属 B 档：每次发布整文件覆盖，需要改列表出参请回到设计器，
 * 或在同包的 {@code ${className}Extension}（C 档）里加工。
 *
 * <p>只装勾选进列表的字段（主键恒在：行操作、编辑、删除都靠它），列名到驼峰的别名写在
 * {@code ${className}Mapper} 的查询里。字典与关联字段这里是<b>取值</b>（字典 code、目标行主键），
 * 中文标签由前端物料按字典解析，不在后端拼进 SQL。
 */
public class ${className}Row {

<@pojo.fields items=rowFields />
<#if audit>
<@auditFields.declarations />
</#if>
<@pojo.accessors items=rowFields />
<#if audit>
<@auditFields.accessors />
</#if>
}

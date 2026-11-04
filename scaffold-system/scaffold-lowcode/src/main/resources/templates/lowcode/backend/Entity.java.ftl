<#import "../_shared/auditFields.ftl" as auditFields>
<#import "../_shared/pojo.ftl" as pojo>
package com.scaffold.business.${module}.domain;

<#list entityImports as type>
import ${type};
</#list>

/**
 * ${modelDesc}（表 {@code ${tableName}}）。
 *
 * <p>本类由低代码设计器按模型定义生成，属 B 档：每次发布整文件覆盖，请不要在这里改业务逻辑，
 * 需要扩展请写到同包的 {@code ${className}Extension}（C 档，生成一次后平台不再覆盖）。
 */
public class ${className} {

<@pojo.fields items=fields withColumn=true />
<#if audit>
<@auditFields.declarations />
</#if>
<@pojo.accessors items=fields />
<#if audit>
<@auditFields.accessors />
</#if>
}

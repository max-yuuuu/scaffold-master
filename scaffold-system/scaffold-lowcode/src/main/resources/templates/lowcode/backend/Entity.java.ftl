<#import "../_shared/auditFields.ftl" as auditFields>
package com.scaffold.business.${module}.domain;

<#list imports as type>
import ${type};
</#list>

/**
 * ${modelDesc}（表 {@code ${tableName}}）。
 *
 * <p>本类由元搭低代码平台按模型定义生成，属 B 档：每次发布整文件覆盖，请不要在这里改业务逻辑，
 * 需要扩展请写到同包的 {@code ${className}Extension}（C 档，生成一次后平台不再覆盖）。
 */
public class ${className} {

<#list fields as f>
    /** ${f.desc}（列 {@code ${f.column}}，类型 {@code ${f.columnType}}） */
    private ${f.javaType} ${f.javaField};

</#list>
<#if audit>
<@auditFields.declarations />
</#if>
<#list fields as f>
    public ${f.javaType} get${f.javaField?cap_first}() { return ${f.javaField}; }
    public void set${f.javaField?cap_first}(${f.javaType} ${f.javaField}) { this.${f.javaField} = ${f.javaField}; }
</#list>
<#if audit>
<@auditFields.accessors />
</#if>
}

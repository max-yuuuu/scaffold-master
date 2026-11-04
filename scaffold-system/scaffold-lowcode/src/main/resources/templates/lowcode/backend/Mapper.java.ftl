<#import "../_shared/queryWhere.ftl" as queryWhere>
<#import "../_shared/methodParams.ftl" as mp>
<#assign entityParam = {"name": entityVar, "javaType": className}>
<#assign operatorParam = {"name": "operator", "javaType": "String"}>
<#assign idParam = {"name": "id", "javaType": "Long"}>
<#assign pagedParams = queryParams + [mp.offsetParam, mp.limitParam]>
<#assign writeParams = [entityParam, operatorParam]>
<#assign updateMethodParams = [idParam] + writeParams>
<#assign deleteMethodParams = (logicDelete && audit)?then([idParam, operatorParam], [idParam])>
package com.scaffold.business.${module}.mapper;

import com.scaffold.business.${module}.domain.${className};
import com.scaffold.business.${module}.domain.${className}Row;<#if !logicDelete>
import org.apache.ibatis.annotations.Delete;</#if>
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

<#list queryParamImports as type>
import ${type};
</#list>
import java.util.List;

/**
 * ${modelDesc}（表 {@code ${tableName}}）的数据访问。
 *
 * <p>本类由元搭低代码平台按模型定义生成，属 B 档：每次发布整文件覆盖，要自定义查询请回到设计器
 * 改字段与查询方式，或在同包的 {@code ${className}Extension}（C 档）里另写。
 *
 * <p>查询条件是「参数为空就是不限制」的<b>静态 SQL</b>，不用动态标签拼 WHERE：任何参数组合下
 * 都是同一条语句，贴着读就知道会查什么；运算符只来自 QueryType 枚举，前端传来的值进不了 SQL 结构。
 * 表名与列名不带反引号 —— MySQL 保留字在建模时就被拦住了（见 {@code SqlIdentifiers}）。
 */
@Mapper
public interface ${className}Mapper {

    /** 列表总数。WHERE 段与 {@link #selectRows} 共用一份，两处口径不会分叉 */
    @Select("""
            SELECT COUNT(*)
            FROM ${tableName} t
            <@queryWhere.where alias="t" />
            """)
    <@mp.declare returnType="long" name="countByQuery" items=queryParams style="mapper" />

    /** 一页列表行。排序固定按主键倒序（最新在前）：不按字段名猜「哪个才是排序列」 */
    @Select("""
            SELECT <#list rowFields as f><#if f?index gt 0>,
                   </#if>t.${f.column}<#if f.column != f.javaField> AS ${f.javaField}</#if></#list>
            FROM ${tableName} t
            <@queryWhere.where alias="t" />
            ORDER BY t.${primaryField.column} DESC
            LIMIT #{offset}, #{limit}
            """)
    <@mp.declare returnType="List<${className}Row>" name="selectRows" items=pagedParams style="mapper" />

<#list uniqueFields as f>
    /** ${f.desc}是否已被占用：新增时 {@code excludeId} 传 null，修改时传自身主键 */
    @Select("""
            SELECT COUNT(*)
            FROM ${tableName}
            WHERE ${f.column} = #{value}<#if logicDelete>
              AND deleted = 0</#if>
              AND (#{excludeId} IS NULL OR ${primaryField.column} <> #{excludeId})
            """)
    long countBy${f.javaField?cap_first}(@Param("value") ${f.javaType} value, @Param("excludeId") Long excludeId);

</#list>
    /**
     * 新增${modelDesc}，自增主键回写进实体的 {@code ${primaryField.javaField}}。
     * <p>创建 / 更新时间不写进语句，交给表上的 {@code DEFAULT CURRENT_TIMESTAMP} 与
     * {@code ON UPDATE CURRENT_TIMESTAMP} —— 与底座同一口径，也让数据库成为时间的唯一出处。
     */
    @Insert("""
            INSERT INTO ${tableName} (<#list insertFields as f>${f.column}<#if f?has_next>, </#if></#list><#if audit>, create_by, update_by</#if>)
            VALUES (<#list insertFields as f>#{${entityVar}.${f.javaField}}<#if f?has_next>, </#if></#list><#if audit>, #{operator}, #{operator}</#if>)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "${primaryField.javaField}")
    <@mp.declare returnType="int" name="insert" items=writeParams style="mapper" />

    /**
     * 按主键覆盖勾选了「可编辑」的字段（表单提交的是整个表单，没勾选的字段一个都不碰）。
     * <p>返回受影响行数，0 就是这一行不存在或已删除，由 Service 换算成业务提示。
     */
    @Update("""
            UPDATE ${tableName}
            SET <#list updateFields as f><#if f?index gt 0>,
                </#if>${f.column} = #{${entityVar}.${f.javaField}}</#list><#if audit>,
                update_by = #{operator}</#if>
            WHERE ${primaryField.column} = #{id}<#if logicDelete> AND deleted = 0</#if>
            """)
    <@mp.declare returnType="int" name="update" items=updateMethodParams style="mapper" />

<#if logicDelete>
    /** 软删：只翻删除标记，历史留在库里 —— 这是模型上勾了「逻辑删除」之后的口径 */
    @Update("""
            UPDATE ${tableName}
            SET deleted = 1<#if audit>, update_by = #{operator}</#if>
            WHERE ${primaryField.column} = #{id} AND deleted = 0
            """)
    <@mp.declare returnType="int" name="deleteById" items=deleteMethodParams style="mapper" />
<#else>
    /** 物理删除：这张表没勾「逻辑删除」，删掉就是真删，历史不可回溯 */
    @Delete("""
            DELETE FROM ${tableName}
            WHERE ${primaryField.column} = #{id}
            """)
    int deleteById(@Param("id") Long id);
</#if>
}

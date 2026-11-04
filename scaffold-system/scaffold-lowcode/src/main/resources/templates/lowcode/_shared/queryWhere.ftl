<#--
  查询条件片段：列表 count、分页取数、导出三处共用同一段，几处口径不会分叉。

  两条铁律（与 QueryType 的注释同口径）：
  1. 运算符只来自 QueryType 枚举，在 Java 侧已定死，前端传什么都不可能改写 SQL 结构；
  2. 每个条件都写成「参数为空就是不限制」，所以不用动态 SQL 拼 WHERE，
     生成的 SQL 在任何参数组合下都是同一条语句，贴着看就知道会查什么。
  区间拆成 xxxStart / xxxEnd 两个条件而不是 BETWEEN —— 一端为空时 BETWEEN 会整条失效。
-->
<#macro where alias>
            WHERE <#if logicDelete>${alias}.deleted = 0<#else>1 = 1</#if>
<#list queryConditions as c>
<#assign col = "${alias}.${c.column}">
<#assign first = "#{${c.params[0].name}}">
<#if c.kind == "LIKE">
              AND (${first} IS NULL OR ${first} = ''
                   OR ${col} LIKE CONCAT('%', ${first}, '%'))
<#elseif c.kind == "BETWEEN">
              AND (${first} IS NULL OR ${col} >= ${first})
              AND (#{${c.params[1].name}} IS NULL OR ${col} <= #{${c.params[1].name}})
<#elseif c.kind == "IN">
              AND (${first} IS NULL OR ${first} = ''
                   OR FIND_IN_SET(${col}, ${first}))
<#else>
              AND (${first} IS NULL OR ${col} ${c.operator} ${first})
</#if>
</#list>
</#macro>

<#--
  方法参数与调用参数的排版，Mapper / Service 共用。

  参数一律逐个一行（底座就是这个形状）：方法签名要能一眼读出「这个查询能按什么筛」。
  把一堆条件塞进一个 Map 或一个查询对象，读的人得再去翻那个对象有什么字段，
  而 MyBatis 的 {@code #{}} 拼错名字是运行期才炸 —— 平铺在签名上最不容易错。
-->

<#-- 分页两个参数：模板里拼在查询参数后面，两处用的类型一致 -->
<#assign offsetParam = {"name": "offset", "javaType": "int"}>
<#assign limitParam = {"name": "limit", "javaType": "int"}>

<#-- style=mapper 时每个参数带 @Param（MyBatis 多参数方法必须具名），plain 时只写类型与名字 -->
<#macro signature items style="plain">
<#list items as p>
<#if style == "mapper">
            @Param("${p.name}") ${p.javaType} ${p.name}<#if p?has_next>,</#if>
<#else>
            ${p.javaType} ${p.name}<#if p?has_next>,</#if>
</#if>
</#list>
</#macro>

<#-- 调用处只要参数名，一行逗号串；prefix 只在真有参数时出现（不然会留一个孤零零的逗号） -->
<#macro call items prefix=""><#if items?size gt 0>${prefix}</#if><#list items as p>${p.name}<#if p?has_next>, </#if></#list></#macro>

<#-- 整条方法声明：没有参数时收成一行的 name()，有参数时逐个一行。
     生成物里「零个查询条件」的模型很常见，两种写法都得留，不能让方法签名挂个空括号。 -->
<#macro declare returnType name items style="plain">
<#if items?size == 0>
    ${returnType} ${name}();
<#else>
    ${returnType} ${name}(
<@signature items=items style=style />
    );
</#if>
</#macro>

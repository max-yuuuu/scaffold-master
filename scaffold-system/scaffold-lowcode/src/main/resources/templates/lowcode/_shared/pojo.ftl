<#--
  POJO 的字段块与访问器块：实体、列表行、写入入参三份产物形状一致，只差在
  「注释里带不带列名与列类型」。

  日期类字段带 @JsonFormat 把出入参钉成前端物料只认的两种规范串
  （yyyy-MM-dd / yyyy-MM-dd HH:mm:ss），不用 Date 承载，避免时区与序列化歧义。
-->
<#macro fields items withColumn=false>
<#list items as f>
    /** ${f.desc}<#if withColumn>（列 {@code ${f.column}}，类型 {@code ${f.columnType}}）</#if> */
<#if f.jsonPattern??>
    @JsonFormat(pattern = "${f.jsonPattern}")
</#if>
    private ${f.javaType} ${f.javaField};

</#list>
</#macro>

<#macro accessors items>
<#list items as f>
    public ${f.javaType} get${f.javaField?cap_first}() { return ${f.javaField}; }
    public void set${f.javaField?cap_first}(${f.javaType} ${f.javaField}) { this.${f.javaField} = ${f.javaField}; }
</#list>
</#macro>

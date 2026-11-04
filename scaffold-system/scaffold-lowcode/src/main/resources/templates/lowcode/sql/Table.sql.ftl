-- ${modelDesc}（模型 ${className}，表 ${tableName}）
-- 由元搭低代码平台按模型定义生成，属 B 档：每次发布整文件覆盖。要改结构请回到设计器改模型，
-- 在这里手改的内容下次发布会被整文件替换掉。
--
-- 怎么执行：新库先跑 sql/V1__init_schema.sql 与 sql/V2__seed_data.sql，再跑本目录里的文件。
-- 本目录不在 docker-entrypoint-initdb.d 的自动执行范围内（mysql 不递归子目录），
-- 建表由人在目标库执行一次 —— 平台不托管项目运行时，也不连项目的库。
--
-- 只建「新表」：CREATE TABLE IF NOT EXISTS 对已存在的表不加列、不删列。
-- 模型加过字段时看合并请求的 diff —— 多出来的那一行就是老库要补的列，请人工执行
-- ALTER TABLE ${tableName} ADD COLUMN ...;（MySQL 没有 IF NOT EXISTS 的加列写法，
-- 把 ALTER 写进本文件会让第二次执行直接报错）。删字段同理：平台永不生成 DROP。

CREATE TABLE IF NOT EXISTS ${tableName} (
<#list fields as f>
    ${f.column} ${f.ddlType} ${f.ddlNull} COMMENT '${f.ddlComment}',
</#list>
<#if logicDelete>
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：1 删除，0 正常',
</#if>
<#if audit>
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
</#if>
    PRIMARY KEY (${primaryField.column})<#if tableIndexes?size gt 0>,</#if>
<#list tableIndexes as i>
    <#if i.unique>UNIQUE KEY<#else>KEY</#if> ${i.name} (${i.column})<#if i?has_next>,</#if>
</#list>
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='${tableComment}';

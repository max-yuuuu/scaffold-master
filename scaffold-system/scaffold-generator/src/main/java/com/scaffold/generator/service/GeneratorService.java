package com.scaffold.generator.service;

import com.scaffold.common.domain.PageQuery;
import com.scaffold.common.domain.PageResult;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.generator.mapper.GeneratorMapper;
import com.scaffold.lowcode.generate.GeneratedFile;
import com.scaffold.lowcode.generate.GeneratedFileZip;
import com.scaffold.lowcode.generate.ModelCodeGenerator;
import com.scaffold.lowcode.model.FieldType;
import com.scaffold.lowcode.model.Naming;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 「导入库里已有的表 → 预览 / 下载代码」这条老链路（方案 §4.4）。
 *
 * <p>PR 5F 起它不再自己拼字符串：把 {@code gen_table} / {@code gen_table_column} 适配成模型形状，
 * 交给与低代码链路同一个 {@link ModelCodeGenerator}，zip 只是换了个出口。
 * 两条链路共用一套模板，才不会各自漂一套生成口径。
 *
 * <p>本模块只负责「给人手工取产物」；推到项目仓库那条在 {@code lowcode/workspace}（PR 7 接发布链路）。
 */
@Service
public class GeneratorService {

    private final GeneratorMapper mapper;
    private final ModelCodeGenerator generator;

    public GeneratorService(GeneratorMapper mapper, ModelCodeGenerator generator) {
        this.mapper = mapper;
        this.generator = generator;
    }

    public PageResult<Map<String, Object>> tables(PageQuery query, String keyword) {
        Page page = pageOf(query);
        return PageResult.of(booleanRows(mapper.selectTables(keyword, page.offset(), page.pageSize()), "synced"),
                mapper.countTables(keyword), page.pageNum(), page.pageSize());
    }

    /** 页面上的配置视图：存下来的开关原样回给前端，生成时的事实（主键、长度、可空）另从库里现查 */
    public List<Map<String, Object>> columns(Long tableId) {
        ensureTable(tableId);
        return booleanRows(mapper.selectColumns(tableId), "insert", "edit", "list", "query", "required");
    }

    public List<Map<String, Object>> importableTables(String keyword) {
        return booleanRows(mapper.selectImportableTables(keyword), "imported");
    }

    @Transactional
    public Map<String, Object> importTables(List<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            throw new BusinessException("请选择要导入的数据库表");
        }
        List<Long> ids = new ArrayList<>();
        for (String tableName : tableNames) {
            Long existingId = mapper.selectTableIdByName(tableName);
            if (existingId != null) {
                ids.add(existingId);
                continue;
            }
            Map<String, Object> dbTable = mapper.selectDatabaseTable(tableName);
            if (dbTable == null) {
                throw new BusinessException("数据库表不存在：" + tableName);
            }
            Map<String, Object> row = tableRow(dbTable);
            mapper.insertTable(row, currentUsername());
            Long tableId = numberAsLong(row.get("id"));
            for (Map<String, Object> column : mapper.selectDatabaseColumns(tableName)) {
                mapper.insertColumn(columnRow(tableId, column));
            }
            ids.add(tableId);
        }
        return Map.of("imported", ids.size(), "ids", ids);
    }

    public Map<String, Object> preview(Long tableId) {
        Map<String, Object> table = ensureTable(tableId);
        String activeFile = table.get("className") + "Controller.java";
        List<Map<String, Object>> files = new ArrayList<>();
        String activeCode = "";
        for (GeneratedFile file : renderFiles(table)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", fileName(file.path()));
            item.put("path", file.path());
            item.put("tier", file.tier().name());
            item.put("code", file.content());
            files.add(item);
            if (file.path().endsWith("/" + activeFile)) {
                activeCode = file.content();
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("files", files);
        data.put("activeFile", activeFile);
        data.put("code", activeCode);
        return data;
    }

    @Transactional
    public void updateTable(Long id, Map<String, Object> request) {
        String tableComment = request == null ? null : asString(request.get("tableComment"));
        Integer synced = null;
        if (request != null && request.containsKey("synced")) {
            synced = Boolean.TRUE.equals(request.get("synced")) ? 1 : 0;
        }
        if (mapper.updateTable(id, tableComment, synced, currentUsername()) == 0) {
            throw new BusinessException("代码生成表不存在");
        }
    }

    public Map<String, Object> generate(Long tableId) {
        Map<String, Object> table = ensureTable(tableId);
        return Map.of(
                "tableName", table.get("tableName"),
                "message", "代码压缩包已准备就绪"
        );
    }

    public byte[] download(Long tableId) {
        return GeneratedFileZip.pack(renderFiles(ensureTable(tableId)));
    }

    /**
     * 一次生成的全部产物。库里读不到的表要当场喊出来 —— 表被删了还在生成，
     * 产物里就是一份指向不存在表的代码，要到运行期才看得出来。
     */
    private List<GeneratedFile> renderFiles(Map<String, Object> table) {
        String tableName = String.valueOf(table.get("tableName"));
        List<Map<String, Object>> dbColumns = mapper.selectDatabaseColumns(tableName);
        if (dbColumns.isEmpty()) {
            throw new BusinessException("数据库表 " + tableName + " 不存在或已被删除，请重新导入");
        }
        Map<String, Object> model = ImportedTableInput.model(table, dbColumns);
        List<Map<String, Object>> fields = ImportedTableInput.fields(table, mapper.selectColumns(numberAsLong(table.get("id"))), dbColumns);
        return generator.renderFiles(model, fields);
    }

    private Map<String, Object> tableRow(Map<String, Object> dbTable) {
        String tableName = String.valueOf(dbTable.get("tableName"));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tableName", tableName);
        row.put("tableComment", dbTable.get("tableComment"));
        row.put("className", Naming.pascal(tableName));
        row.put("module", moduleName(tableName));
        row.put("businessName", businessName(tableName));
        return row;
    }

    /**
     * 导入时存下的列配置。类型事实不在这里算第二次：Java 类型、默认控件、默认查询方式都问
     * {@link FieldType}（方案 §2.2 之二：映射只有一份）。
     */
    private Map<String, Object> columnRow(Long tableId, Map<String, Object> column) {
        String columnName = String.valueOf(column.get("columnName"));
        FieldType type = FieldType.ofColumn(String.valueOf(column.get("dataType")));
        boolean primary = "PRI".equalsIgnoreCase(String.valueOf(column.get("columnKey")));
        boolean autoIncrement = String.valueOf(column.get("extra")).toLowerCase(Locale.ROOT).contains("auto_increment");
        boolean writable = !primary && ImportedTableInput.isWritable(columnName);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tableId", tableId);
        row.put("columnName", columnName);
        row.put("javaField", Naming.camel(columnName));
        row.put("javaType", type.javaType(autoIncrement));
        row.put("jdbcType", String.valueOf(column.get("dataType")).toLowerCase(Locale.ROOT));
        row.put("columnComment", column.get("columnComment"));
        row.put("insert", writable ? 1 : 0);
        row.put("edit", writable ? 1 : 0);
        row.put("list", ImportedTableInput.isHidden(columnName) ? 0 : 1);
        row.put("query", queryField(columnName) && !ImportedTableInput.isHidden(columnName) ? 1 : 0);
        row.put("queryType", type.defaultQueryType().operator());
        row.put("formType", type.defaultFormType().code());
        row.put("required", "NO".equals(String.valueOf(column.get("nullable"))) && !primary ? 1 : 0);
        row.put("sort", column.get("sort"));
        return row;
    }

    /** 模块段取表名前缀，与底座既有习惯一致（sys_* 归 system，demo_product 归 demo） */
    private String moduleName(String tableName) {
        int split = tableName.indexOf('_');
        String module = split > 0 ? tableName.substring(0, split) : "system";
        return "sys".equals(module) ? "system" : Naming.snake(module);
    }

    private String businessName(String tableName) {
        int split = tableName.indexOf('_');
        return Naming.camel(split > 0 ? tableName.substring(split + 1) : tableName);
    }

    /** 老链路没有配置界面去逐个勾查询条件，导入时按列名猜几个高频的：名称、编码、状态、类型 */
    private boolean queryField(String columnName) {
        String lower = columnName.toLowerCase(Locale.ROOT);
        return lower.contains("name") || lower.contains("code") || lower.contains("status") || lower.contains("type");
    }

    private Map<String, Object> ensureTable(Long tableId) {
        Map<String, Object> table = mapper.selectTableById(tableId);
        if (table == null) {
            throw new BusinessException("代码生成表不存在");
        }
        return table;
    }

    private String fileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private List<Map<String, Object>> booleanRows(List<Map<String, Object>> rows, String... keys) {
        for (Map<String, Object> row : rows) {
            for (String key : keys) {
                Object value = row.get(key);
                if (value instanceof Number number) {
                    row.put(key, number.intValue() == 1);
                }
            }
        }
        return rows;
    }

    private Page pageOf(PageQuery query) {
        int pageNum = query == null ? PageQuery.DEFAULT_PAGE_NUM : query.normalizedPageNum();
        int pageSize = query == null ? PageQuery.DEFAULT_PAGE_SIZE : query.normalizedPageSize();
        return new Page(pageNum, pageSize);
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : String.valueOf(authentication.getPrincipal());
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long numberAsLong(Object value) {
        return value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
    }

    private record Page(int pageNum, int pageSize) {
        int offset() {
            return (pageNum - 1) * pageSize;
        }
    }
}

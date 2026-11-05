package com.scaffold.lowcode.generate;

import com.scaffold.common.exception.BusinessException;
import com.scaffold.lowcode.design.DesignNode;
import com.scaffold.lowcode.design.PageTarget;
import com.scaffold.lowcode.model.FieldType;
import com.scaffold.lowcode.model.Naming;
import com.scaffold.lowcode.model.QueryType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 把一个模型的行数据（{@code lc_model} + 它的 {@code lc_field}）装配成 FreeMarker 的数据模型（方案 §4.2）。
 *
 * <p>入参形状是「字段名 → 值」的宽松 Map，不绑死数据来源：低代码链路给的是库里查出来的 {@code lc_*} 行，
 * 老代码生成页给的是 {@code gen_table} / {@code gen_table_column} 拼出来的同形状 Map —— 两条链路共用一套模板。
 *
 * <p>所有派生都在这一层算完：命名（类名 / 包段 / 资源名 / 表名）、列类型全形式、查询运算符、
 * 需要的 import、列表 / 查询 / 表单三组字段的筛选。模板里只允许取值与循环 ——
 * 生成的代码要给人读，模板本身也得给人读，把判断堆进 {@code .ftl} 只会两头都读不动。
 */
public final class GenerationInput {

    private GenerationInput() {
    }

    public static Map<String, Object> of(Map<String, Object> model, List<Map<String, Object>> fields) {
        return of(model, fields, null);
    }

    /**
     * @param target 页面目标；{@code null} 表示没有设计（zip 下载与老代码生成页），
     *               此时页面形状回到模型上的 {@code is_list} / {@code is_query} / {@code is_insert} 三个勾选
     */
    public static Map<String, Object> of(Map<String, Object> model, List<Map<String, Object>> fields, PageTarget target) {
        String modelCode = text(model.get("modelCode"));
        String className = blankToNull(text(model.get("className")));
        if (className == null) {
            className = Naming.pascal(modelCode);
        }
        String module = blankToNull(text(model.get("moduleName")));
        if (module == null) {
            module = Naming.packageSegment(modelCode);
        }
        String tableName = blankToNull(text(model.get("tableName")));
        if (tableName == null) {
            tableName = Naming.snake(modelCode);
        }
        String resourceName = Naming.camel(modelCode);
        String modelName = blankToNull(text(model.get("modelName")));
        if (modelName == null) {
            modelName = className;
        }

        List<Map<String, Object>> fieldModels = new ArrayList<>();
        List<Map<String, Object>> listFields = new ArrayList<>();
        List<Map<String, Object>> queryFields = new ArrayList<>();
        List<Map<String, Object>> formFields = new ArrayList<>();
        List<Map<String, Object>> insertFields = new ArrayList<>();
        List<Map<String, Object>> updateFields = new ArrayList<>();
        List<Map<String, Object>> uniqueFields = new ArrayList<>();
        Map<String, Object> primary = null;

        for (Map<String, Object> field : fields) {
            Map<String, Object> item = fieldModel(field);
            fieldModels.add(item);
            if (Boolean.TRUE.equals(item.get("primary"))) {
                primary = item;
            }
            if (Boolean.TRUE.equals(item.get("list"))) {
                listFields.add(item);
            }
            if (Boolean.TRUE.equals(item.get("query"))) {
                queryFields.add(item);
            }
            if (Boolean.TRUE.equals(item.get("primary")) && Boolean.TRUE.equals(item.get("autoIncrement"))) {
                // 自增主键不进表单：值由数据库给，进表单等于让人猜下一个 id，
                // 也让页面的表单类型多出重复成员（vue-tsc 当场报错）。字段接口给默认值时也压掉这三格，
                // 但这里不能只依赖上游干净 —— 老链路带过来的配置与存量数据都可能还勾着
                item.put("insert", false);
                item.put("edit", false);
                item.put("required", false);
            }
            if (Boolean.TRUE.equals(item.get("insert")) || Boolean.TRUE.equals(item.get("edit"))) {
                formFields.add(item);
            }
            // 自增主键不进 INSERT：由数据库给，写进去反而会把 AUTO_INCREMENT 顶掉
            if (Boolean.TRUE.equals(item.get("insert")) && !Boolean.TRUE.equals(item.get("autoIncrement"))) {
                insertFields.add(item);
            }
            if (Boolean.TRUE.equals(item.get("edit")) && !Boolean.TRUE.equals(item.get("primary"))) {
                updateFields.add(item);
            }
            // 唯一性检查要能拿到新值，所以只对「可录入 / 可编辑」的字段生成；
            // 不可编辑的唯一列（比如外部系统回填的编号）没有写入路径，检查也无从查起
            if (Boolean.TRUE.equals(item.get("unique"))
                    && (Boolean.TRUE.equals(item.get("insert")) || Boolean.TRUE.equals(item.get("edit")))) {
                uniqueFields.add(item);
            }
        }
        if (primary == null) {
            throw new BusinessException("模型 " + modelName + " 没有主键字段，生成物无法定位单条记录");
        }
        // 主键的类型要能对上路径参数：五个入口写的是 @PathVariable Long id（与底座同一形状），
        // 字符串主键的模型编得过但调不通 —— 路径上拿到的是编号，Service 按数字查。当场拦掉比留到运行期好
        String keyJavaType = text(primary.get("javaType"));
        if (!"Long".equals(keyJavaType) && !"Integer".equals(keyJavaType)) {
            throw new BusinessException("模型 " + modelName + " 的主键「" + primary.get("name") + "」是 "
                    + keyJavaType + " 类型，本期生成器只支持数字主键（Long / Integer）。"
                    + "请把业务编号做成唯一列并另加一个数字主键，那样按编号查询照样生成得出来");
        }
        // 列表行 = 主键 + 勾选进列表的字段（主键恒在：行操作、编辑、删除都要靠它）
        List<Map<String, Object>> rowFields = new ArrayList<>();
        for (Map<String, Object> item : fieldModels) {
            if (Boolean.TRUE.equals(item.get("primary")) || Boolean.TRUE.equals(item.get("list"))) {
                rowFields.add(item);
            }
        }
        List<Map<String, Object>> tableColumns = rowFields;

        // 有设计时，页面的形由设计说：查询区、列、表单各按设计里的成员与顺序，
        // 类型与约束仍来自上面按模型算好的那些字段（方案 §1.5 ②「页面是形、模型是料」）
        if (target != null) {
            Map<String, Map<String, Object>> byCode = indexByCode(fieldModels);
            queryFields = designQueryFields(target.design().search(), byCode);
            formFields = designFormFields(target.design().form(), byCode);
            insertFields = writableSubset(formFields, true);
            updateFields = writableSubset(formFields, false);
            uniqueFields = uniqueSubset(formFields);
            tableColumns = designColumns(target.design().columns(), byCode);
            rowFields = withPrimary(primary, tableColumns);
            listFields = tableColumns;
        } else {
            normalizeColumns(rowFields);
        }
        // 这两条不是洁癖：字段全空时生成的 INSERT / UPDATE 会是缺列的残缺语句，
        // 推出去要到运行期才炸，当场拦掉更省事。带设计时算的是设计筛完的那两份
        if (insertFields.isEmpty()) {
            throw new BusinessException("模型 " + modelName + " 没有任何「可录入」字段，生成不出新增语句"
                    + (target == null ? "" : "（页面的表单弹窗里也要至少摆一格可录入的字段）"));
        }
        if (updateFields.isEmpty()) {
            throw new BusinessException("模型 " + modelName + " 没有任何「可编辑」字段，生成不出修改语句"
                    + (target == null ? "" : "（页面的表单弹窗里也要至少摆一格可编辑的字段）"));
        }

        List<Map<String, Object>> queryConditions = queryConditions(queryFields);
        List<Map<String, Object>> queryParams = flattenParams(queryConditions);

        Map<String, Object> root = new LinkedHashMap<>();
        boolean audit = truth(model.get("enableAuditField"));
        root.put("module", module);
        root.put("className", className);
        root.put("resourceName", resourceName);
        root.put("tableName", tableName);
        root.put("modelName", modelName);
        root.put("modelDesc", blankToDefault(text(model.get("modelDesc")), modelName));
        String resourceSlug = Naming.snake(resourceName);
        String permissionPrefix = module + ":" + resourceSlug;
        root.put("permissionPrefix", permissionPrefix);
        String apiPath = "/" + module + "/" + resourceSlug;
        root.put("apiPath", apiPath);
        root.put("tree", truth(model.get("isTree")));
        root.put("logicDelete", truth(model.get("enableLogicDelete")));
        root.put("dataScope", truth(model.get("enableDataScope")));
        root.put("audit", audit);
        root.put("fields", fieldModels);
        root.put("listFields", listFields);
        root.put("queryFields", queryFields);
        root.put("formFields", formFields);
        root.put("insertFields", insertFields);
        root.put("updateFields", updateFields);
        root.put("uniqueFields", uniqueFields);
        root.put("rowFields", rowFields);
        root.put("tableColumns", tableColumns);
        root.put("queryConditions", queryConditions);
        root.put("queryParams", queryParams);
        // Controller 要不要 import @DateTimeFormat：日期类查询参数得按规范串绑定
        root.put("hasDateQuery", queryParams.stream().anyMatch(p -> p.get("jsonPattern") != null));
        // Mapper / Service / Controller 的方法签名直接用查询参数的类型，这几个 import 归它们
        root.put("queryParamImports", typeImportsOf(queryParams));
        // Mapper 里的实体参数名（@Param("order") Order order），与底座 @Param("row") 那个位置同职责
        root.put("entityVar", Naming.camel(className));
        root.put("primaryField", primary);
        root.put("tableIndexes", tableIndexes(fieldModels));
        root.put("tableComment", sqlText(blankToDefault(text(model.get("modelDesc")), modelName)));
        // 菜单：component 就是前端路由解析的路径（composables/auth.ts 里 ../views/{component}.vue）
        root.put("menuComponent", module + "/" + className);
        root.put("menuPath", target == null || target.routePath() == null ? apiPath : target.routePath());
        root.put("menuName", target == null ? blankToDefault(text(model.get("modelDesc")), modelName) : target.pageName());
        root.put("menuIcon", target == null || blankToNull(target.menuIcon()) == null ? "grid" : target.menuIcon());
        root.put("menuSort", target == null || target.menuSort() == null ? 1 : target.menuSort());
        root.put("resourceSlug", resourceSlug);
        // 菜单里的按钮权限点跟着页面真摆了哪些动作走：设计里没有删除，就不种一个没人用的 :remove
        List<Map<String, Object>> menuButtons = menuButtonsOf(target);
        List<String> menuSuffixes = new ArrayList<>();
        menuSuffixes.add("list");
        for (Map<String, Object> button : menuButtons) {
            menuSuffixes.add(String.valueOf(button.get("suffix")));
        }
        root.put("menuPermissionList", menuSuffixes.stream()
                .map(suffix -> "'" + permissionPrefix + ":" + suffix + "'")
                .collect(Collectors.joining(", ")));
        root.put("menuButtons", menuButtons);
        // 页面顶部与按钮：形状由设计给，没有设计就回到 PR 5 那三件固定动作
        root.put("pageTitle", target == null ? blankToDefault(text(model.get("modelDesc")), modelName) + "管理"
                : blankToDefault(target.design().title(), blankToDefault(target.pageName(), modelName)));
        root.put("pageDesc", target == null || target.design().description() == null
                ? "本页由低代码设计器按模型定义生成；要改字段回到设计器改模型再发布。" : target.design().description());
        root.put("toolbarButtons", pageButtons(target, "Toolbar", modelName, permissionPrefix));
        root.put("rowButtons", pageButtons(target, "RowActions", modelName, permissionPrefix));
        root.put("hasExport", target == null || hasExport(target));
        // 前端产物用：接口对象名、导出文件名、页面要 import 的物料组件
        root.put("apiVar", Naming.camel(className) + "Api");
        root.put("exportName", blankToDefault(text(model.get("modelDesc")), modelName) + ".csv");
        root.put("modelNameJs", jsText(modelName));
        root.put("modelDescJs", jsText(blankToDefault(text(model.get("modelDesc")), modelName)));
        root.put("pageImports", pageImportsOf(queryParams, formFields, rowFields));
        // import 顺序固定，同一份模型两次生成的文本才逐字节一致（发布 diff 才可读）；
        // 每个文件只带自己用到的，不留无用 import
        root.put("entityImports", audit ? withAudit(importsOf(fieldModels)) : importsOf(fieldModels));
        root.put("rowImports", audit ? withAudit(importsOf(rowFields)) : importsOf(rowFields));
        root.put("writeImports", importsOf(formFields));
        return root;
    }

    /** 页面里出现导出动作：ListPage 表头的导出按钮只在接了 exporter 时才出现 */
    private static boolean hasExport(PageTarget target) {
        return actionsOf(target, "Toolbar").contains("export") || actionsOf(target, "RowActions").contains("export");
    }

    /**
     * 菜单要种的按钮权限点。
     *
     * <p>名字固定用「新增/编辑/删除」而不是设计里 person 写的按钮文案：这一行是权限点的名字，
     * 跟项目里其它模块的菜单同一读法，也跟着底座的 {@code system:post:*} 口径。
     */
    private static List<Map<String, Object>> menuButtonsOf(PageTarget target) {
        if (target == null) {
            return MENU_BUTTONS;
        }
        List<String> actions = new ArrayList<>(actionsOf(target, "Toolbar"));
        // 行操作里的动作要并进来而不是「只留工具栏里已经有的」：编辑与删除通常只出现在行上，
        // 按交集筛就把它们全丢了 —— 页面上按钮在、菜单不种权限点，点下去是 403
        actionsOf(target, "RowActions").stream().filter(action -> !actions.contains(action)).forEach(actions::add);
        List<Map<String, Object>> buttons = new ArrayList<>();
        int sort = 1;
        for (String suffix : List.of("add", "edit", "remove")) {
            boolean present = actions.stream().anyMatch(action -> suffixOf(action).equals(suffix));
            if (present) {
                buttons.add(Map.of("action", MENU_BUTTON_NAMES.get(suffix), "suffix", suffix, "sort", sort++));
            }
        }
        return buttons;
    }

    private static final Map<String, String> MENU_BUTTON_NAMES = Map.of("add", "新增", "edit", "编辑", "remove", "删除");

    /** 按钮上出现过的动作（去重、按出现顺序） */
    private static List<String> actionsOf(PageTarget target, String blockType) {
        List<String> actions = new ArrayList<>();
        for (DesignNode node : target.design().childrenOf(blockType)) {
            String action = node.text("action");
            if (action != null && !actions.contains(action)) {
                actions.add(action);
            }
        }
        return actions;
    }

    /**
     * 生成页面上的按钮。
     *
     * <p>动作决定接线（点下去调哪个方法、要哪个权限点），设计只决定文案与样式：
     * 让人手写权限串就会出现「菜单里权限点齐着、页面上按钮永远灰」。
     * {@code export} 不产出按钮 —— 导出是 ListPage 表头的固定能力，页面上再放一个是两个入口。
     */
    private static List<Map<String, Object>> pageButtons(PageTarget target, String blockType, String modelName, String prefix) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (target == null) {
            if ("Toolbar".equals(blockType)) {
                out.add(button("create", "新增" + modelName, "primary", false, prefix));
            } else {
                out.add(button("edit", "编辑", null, false, prefix));
                out.add(button("delete", "删除", null, true, prefix));
            }
            return out;
        }
        for (DesignNode node : target.design().childrenOf(blockType)) {
            String action = node.text("action");
            if (action == null || "export".equals(action)) {
                continue;
            }
            Map<String, Object> item = button(action, node.text("label", actionName(action)), node.text("variant", null),
                    node.flag("danger", false), prefix);
            out.add(item);
        }
        return out;
    }

    private static Map<String, Object> button(String action, String label, String variant, boolean danger, String prefix) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("action", action);
        item.put("label", jsText(label));
        item.put("rawLabel", label);
        item.put("variant", blankToNull(variant));
        item.put("danger", danger);
        item.put("icon", ICONS.getOrDefault(action, "dot"));
        item.put("click", CLICKS.getOrDefault(action, "list.load()"));
        item.put("perm", prefix + ":" + suffixOf(action));
        return item;
    }

    private static final Map<String, String> ICONS = Map.of("create", "plus", "edit", "edit", "delete", "trash", "refresh", "refresh");
    private static final Map<String, String> CLICKS = Map.of("create", "openCreate", "edit", "openEdit(row)", "delete", "remove(row)", "refresh", "list.load()");
    private static final Map<String, String> ACTION_NAMES = Map.of("create", "新增", "edit", "编辑", "delete", "删除", "export", "导出", "refresh", "刷新");

    private static String actionName(String action) {
        return ACTION_NAMES.getOrDefault(action, action);
    }

    /** 动作 → 权限点后缀，与 {@code ComponentCatalog.permissionSuffix} 同一份口径 */
    private static String suffixOf(String action) {
        return switch (action) {
            case "create" -> "add";
            case "edit" -> "edit";
            case "delete" -> "remove";
            default -> "list";
        };
    }

    /** 单个字段的模板视图：命名、列类型全形式与查询运算符都在这里定稿 */
    /** 菜单里的三个按钮权限点。导出与列表共用 {@code :list}，与底座的 {@code system:post:list} 同一口径 */
    private static final List<Map<String, Object>> MENU_BUTTONS = List.of(
            Map.of("action", "新增", "suffix", "add", "sort", 1),
            Map.of("action", "编辑", "suffix", "edit", "sort", 2),
            Map.of("action", "删除", "suffix", "remove", "sort", 3));

    /**
     * SQL 字符串字面量里的内容：单引号翻倍，反斜杠也翻倍。
     *
     * <p>字段说明是人在设计器里打的中文，里面出现引号很常见（{@code 状态'正常'}），
     * 不转义就是建表语句少一个引号，报的错还指着半行提示。
     */
    private static String sqlText(String value) {
        return value.replace("\\", "\\\\").replace("'", "''");
    }

    private static Map<String, Object> fieldModel(Map<String, Object> field) {
        String fieldCode = text(field.get("fieldCode"));
        String javaType = blankToDefault(text(field.get("javaType")), "String");
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", fieldCode);
        item.put("name", blankToDefault(text(field.get("fieldName")), fieldCode));
        item.put("desc", blankToDefault(text(field.get("fieldDesc")), blankToDefault(text(field.get("fieldName")), fieldCode)));
        item.put("javaField", Naming.camel(fieldCode));
        item.put("javaType", javaType);
        item.put("column", blankToDefault(text(field.get("columnName")), Naming.snake(fieldCode)));
        item.put("columnType", columnType(field));
        item.put("primary", truth(field.get("isPrimary")));
        item.put("autoIncrement", truth(field.get("isAutoIncrement")));
        item.put("nullable", truth(field.get("isNullable")));
        item.put("unique", truth(field.get("isUnique")));
        item.put("required", truth(field.get("isRequired")));
        item.put("list", truth(field.get("isList")));
        item.put("query", truth(field.get("isQuery")));
        item.put("queryKind", QueryType.of(text(field.get("queryType"))).name());
        item.put("queryOperator", QueryType.of(text(field.get("queryType"))).operator());
        item.put("jsonPattern", jsonPattern(javaType));
        item.put("stringType", "String".equals(javaType));
        item.put("insert", truth(field.get("isInsert")));
        item.put("edit", truth(field.get("isEdit")));
        item.put("formType", blankToDefault(text(field.get("formType")), "INPUT"));
        item.put("dictType", blankToNull(text(field.get("dictType"))));
        item.put("defaultValue", blankToNull(text(field.get("defaultValue"))));
        item.put("textType", String.valueOf(item.get("columnType")).toLowerCase(Locale.ROOT).startsWith("text"));
        item.put("ddlType", String.valueOf(item.get("columnType")).toUpperCase(Locale.ROOT));
        item.put("ddlComment", sqlText(String.valueOf(item.get("desc"))));
        item.put("ddlNull", ddlNull(item));
        item.put("tsType", tsType(javaType));
        // 数字输入框的小数位：只有 BigDecimal 需要。NumberInput 不传 precision 时按 step（默认 1）推断，
        // 于是金额输入 1234.5 会被静默取整成 1235 —— 不报错也不失败，直接把数据写错
        item.put("numberPrecision", "BigDecimal".equals(javaType) ? decimalScale(field) : null);
        item.put("descJs", jsText(String.valueOf(item.get("desc"))));
        // 前端接口里可空与否跟着「必填 / 主键」走：可空字段类型上要带 | null，编辑表单才知道能放 null
        item.put("nullableTs", !Boolean.TRUE.equals(item.get("required")) && !Boolean.TRUE.equals(item.get("primary")));
        String formControl = formControl(String.valueOf(item.get("formType")), item.get("dictType") != null, javaType);
        item.put("formControl", formControl);
        // 提交前 trim 掉首尾空格；下拉、字典与日期的取值不是人打的自由文本，不动它
        item.put("valueTrim", "string".equals(item.get("tsType")) && !isChoice(formControl));
        item.put("inputVerb", isChoice(formControl) ? "请选择" : "请输入");
        // 前端必填 = 模型必填或设计标了必填（只能收紧）；后端注解只认模型，两边不同口径时以数据库约束为准
        item.put("inputRequired", truth(field.get("isRequired")));
        item.put("placeholder", null);
        return item;
    }

    /**
     * 建表语句里「这一列的约束与默认值」那一段，形状与底座 {@code sql/V1__init_schema.sql} 一致：
     * 自增主键 {@code NOT NULL AUTO_INCREMENT}，必填 {@code NOT NULL}，可空的文本列只写 {@code NULL}
     * （MySQL 里 TEXT 不能带默认值），其余 {@code DEFAULT NULL}。
     */
    private static String ddlNull(Map<String, Object> item) {
        boolean primary = Boolean.TRUE.equals(item.get("primary"));
        boolean autoIncrement = Boolean.TRUE.equals(item.get("autoIncrement"));
        StringBuilder ddl = new StringBuilder();
        if (autoIncrement && primary) {
            ddl.append("NOT NULL AUTO_INCREMENT");
        } else if (primary || Boolean.TRUE.equals(item.get("required")) || Boolean.FALSE.equals(item.get("nullable"))) {
            ddl.append("NOT NULL");
        } else {
            ddl.append(Boolean.TRUE.equals(item.get("textType")) ? "NULL" : "DEFAULT NULL");
        }
        String defaultValue = (String) item.get("defaultValue");
        // MySQL 不允许 TEXT 列带默认值，填了也只能忽略（写进去就是建表直接失败）
        if (defaultValue != null && !Boolean.TRUE.equals(item.get("textType"))) {
            // CURRENT_TIMESTAMP 是唯一不加引号的写法：加了引号就成一个固定时间串了
            ddl.append(" DEFAULT ").append("CURRENT_TIMESTAMP".equalsIgnoreCase(defaultValue)
                    ? defaultValue.toUpperCase(Locale.ROOT) : "'" + sqlText(defaultValue) + "'");
        }
        return ddl.toString();
    }

    /**
     * 建表要生成的二级索引：唯一列出 {@code UNIQUE KEY}，非模糊的查询列与关联列出普通 {@code KEY}。
     *
     * <p>LIKE 查询列不建索引 —— 生成的是 {@code LIKE '%x%'} 前置通配，用不上索引，白占写入开销。
     * 文本列也不建 —— MySQL 对 TEXT 建索引必须指定前缀长度，不给前缀就是建表失败。
     */
    private static List<Map<String, Object>> tableIndexes(List<Map<String, Object>> fields) {
        Map<String, Map<String, Object>> byColumn = new LinkedHashMap<>();
        for (Map<String, Object> field : fields) {
            if (Boolean.TRUE.equals(field.get("textType")) || Boolean.TRUE.equals(field.get("primary"))) {
                continue;
            }
            String column = String.valueOf(field.get("column"));
            boolean unique = Boolean.TRUE.equals(field.get("unique"));
            boolean indexed = unique
                    || (Boolean.TRUE.equals(field.get("query")) && !"LIKE".equals(field.get("queryKind")))
                    || "REF".equals(field.get("formType")) || "Long".equals(field.get("javaType"));
            if (!indexed) {
                continue;
            }
            Map<String, Object> index = byColumn.get(column);
            if (index == null) {
                index = new LinkedHashMap<>();
                index.put("name", (unique ? "uk_" : "idx_") + column);
                index.put("column", column);
                index.put("unique", unique);
                byColumn.put(column, index);
            } else if (unique) {
                // 同一列既唯一又被查询过：唯一的约束更强，名字与写法都换成唯一
                index.put("name", "uk_" + column);
                index.put("unique", true);
            }
        }
        return List.copyOf(byColumn.values());
    }

    /**
     * 日期类字段的出入参格式，与前端物料 {@code dateValue} 只认的两种规范串一致
     * （{@code yyyy-MM-dd} / {@code yyyy-MM-dd HH:mm:ss}）。
     *
     * <p>为什么在字段上写 {@code @JsonFormat} 而不是改全局配置：全局那条在 A 档里，
     * 平台不该为了一个模型去动交付项目的 Jackson 设置；写在生成字段上，改一个模型只影响这一列。
     *
     * @return 需要注解时返回格式串，其余类型返回 {@code null}
     */
    private static String jsonPattern(String javaType) {
        return switch (javaType) {
            case "LocalDate" -> "yyyy-MM-dd";
            case "LocalDateTime" -> "yyyy-MM-dd HH:mm:ss";
            default -> null;
        };
    }

    /** 一个文件用到的 import：字段类型里的 java.time / java.math，加出现日期字段时的 Jackson 注解 */
    private static List<String> importsOf(List<Map<String, Object>> fields) {
        Set<String> imports = new LinkedHashSet<>(typeImportsOf(fields));
        if (fields.stream().anyMatch(f -> f.get("jsonPattern") != null)) {
            imports.add("com.fasterxml.jackson.annotation.JsonFormat");
        }
        return sorted(imports);
    }

    /** 只按 javaType 收 {@code java.time} / {@code java.math}：方法签名里的参数类型也走这条 */
    private static List<String> typeImportsOf(List<Map<String, Object>> items) {
        Set<String> imports = new LinkedHashSet<>();
        for (Map<String, Object> item : items) {
            String javaType = String.valueOf(item.get("javaType"));
            switch (javaType) {
                case "BigDecimal" -> imports.add("java.math.BigDecimal");
                case "LocalDate" -> imports.add("java.time.LocalDate");
                case "LocalDateTime" -> imports.add("java.time.LocalDateTime");
                default -> {
                }
            }
        }
        return sorted(imports);
    }

    /** 审计四字段里的创建 / 更新时间固定是 LocalDateTime，且要按规范串出参 */
    private static List<String> withAudit(List<String> imports) {
        Set<String> merged = new LinkedHashSet<>(imports);
        merged.add("java.time.LocalDateTime");
        merged.add("com.fasterxml.jackson.annotation.JsonFormat");
        return sorted(merged);
    }

    /**
     * Java 类型 → TypeScript 类型。日期类固定映射成 {@code string}：后端在生成字段上写
     * {@code @JsonFormat}，出参就是 {@code yyyy-MM-dd} / {@code yyyy-MM-dd HH:mm:ss} 规范串，
     * 与前端物料 {@code dateValue} 只认的两种写法一致 —— 全程不用 Date 对象承载，避免时区与序列化歧义。
     */
    private static String tsType(String javaType) {
        return switch (javaType) {
            case "Integer", "Long", "BigDecimal", "Double", "Float" -> "number";
            default -> "string";
        };
    }

    /**
     * 表单控件。挂了字典的一律走 {@code DictSelect}（选项由注入的 dict 能力取）；
     * 没挂字典的单选 / 多选、附件与关联先落文本框并留注释 —— 模板不替项目猜选项与上传接口从哪来。
     */
    private static String formControl(String formType, boolean hasDict, String javaType) {
        if (hasDict) {
            return "dict";
        }
        return switch (formType) {
            case "TEXTAREA" -> "textarea";
            case "NUMBER" -> "number";
            case "DATE" -> "date";
            case "DATETIME" -> "datetime";
            case "SELECT", "RADIO", "CHECKBOX" -> "select";
            case "UPLOAD" -> "upload";
            case "REF" -> "ref";
            default -> "input";
        };
    }

    private static boolean isChoice(String control) {
        return switch (control) {
            case "dict", "select", "date", "datetime" -> true;
            default -> false;
        };
    }

    /**
     * TS / JS 单引号字符串字面量里的内容。字段说明是中文自由文本，出现引号或反斜杠就转义掉 ——
     * 生成的 .vue 少一个转义就是整份文件解析失败，而这种错要到前端构建才暴露。
     */
    private static String jsText(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    /**
     * 生成页面要 import 的物料组件名：只列这个页面真会用到的。
     * 物料是 npm 依赖，多一个没用的 import 在 {@code vue-tsc} 下只是噪音，
     * 但读代码的人会以为页面里有这个控件。{@code Field} / {@code ListPage} /
     * {@code FormDialog} / {@code PermissionButton} 四个是这套版式的固定件。
     */
    private static Map<String, Object> pageImportsOf(List<Map<String, Object>> queryItems,
                                                     List<Map<String, Object>> formItems,
                                                     List<Map<String, Object>> rowItems) {
        Set<String> ui = new LinkedHashSet<>(List.of("Field"));
        Set<String> business = new LinkedHashSet<>(List.of("FormDialog", "ListPage", "PermissionButton"));
        for (Map<String, Object> item : concat(queryItems, formItems)) {
            switch (String.valueOf(item.get("formControl"))) {
                case "textarea" -> ui.add("Textarea");
                case "number" -> ui.add("NumberInput");
                case "date", "datetime" -> ui.add("DateInput");
                case "select" -> ui.add("Select");
                case "dict" -> business.add("DictSelect");
                default -> ui.add("TextInput");
            }
        }
        if (rowItems.stream().anyMatch(item -> item.get("dictType") != null)) {
            business.add("DictTag");
        }
        Map<String, Object> imports = new LinkedHashMap<>();
        imports.put("ui", sorted(ui));
        imports.put("business", sorted(business));
        return imports;
    }

    private static List<Map<String, Object>> concat(List<Map<String, Object>> first, List<Map<String, Object>> second) {
        List<Map<String, Object>> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    private static List<String> sorted(Set<String> imports) {
        return imports.stream().sorted().toList();
    }

    /**
     * 查询条件摊平成「SQL 用的一组」+「方法签名用的一组」。
     *
     * <p>口径与底座一致：条件写成 {@code (#{x} IS NULL OR ...)}，不填就是不限制，
     * 不用动态 SQL 拼 WHERE；运算符只来自 {@link QueryType} 枚举，前端传什么都不可能改写 SQL 结构。
     * 区间拆成 {@code xxxStart} / {@code xxxEnd} 两个参数（比 {@code BETWEEN} 少一个空值陷阱），
     * 多选收一个逗号串走 {@code FIND_IN_SET}（免掉 {@code <foreach>}，注解 SQL 里能写完整）。
     */
    private static List<Map<String, Object>> queryConditions(List<Map<String, Object>> queryFields) {
        List<Map<String, Object>> conditions = new ArrayList<>();
        for (Map<String, Object> field : queryFields) {
            String kind = String.valueOf(field.get("queryKind"));
            String javaField = String.valueOf(field.get("javaField"));
            String javaType = String.valueOf(field.get("javaType"));
            String pattern = (String) field.get("jsonPattern");
            Object dictType = field.get("dictType");
            boolean hasDict = dictType != null;
            List<Map<String, Object>> params = new ArrayList<>();
            Object placeholder = field.get("placeholder");
            if ("BETWEEN".equals(kind)) {
                // 区间两端按类型给控件（日期区间是两个日期框），不按字典渲染
                params.add(queryParam(javaField + "Start", javaType, pattern, field.get("desc") + "（起）",
                        queryControl(javaType, false), null, field.get("numberPrecision"), placeholder));
                params.add(queryParam(javaField + "End", javaType, pattern, field.get("desc") + "（止）",
                        queryControl(javaType, false), null, field.get("numberPrecision"), placeholder));
            } else if ("IN".equals(kind)) {
                // 后端按 FIND_IN_SET 收逗号串，这里就如实给一个文本框，不假装是多选控件
                params.add(queryParam(javaField, "String", null, field.get("desc") + "（多选，逗号分隔）", "input", null, null, placeholder));
            } else {
                params.add(queryParam(javaField, javaType, pattern, field.get("desc"),
                        queryControl(javaType, hasDict), dictType, field.get("numberPrecision"), placeholder));
            }
            Map<String, Object> condition = new LinkedHashMap<>();
            condition.put("kind", kind);
            condition.put("operator", field.get("queryOperator"));
            condition.put("column", field.get("column"));
            condition.put("params", params);
            conditions.add(condition);
        }
        return conditions;
    }

    /** 方法签名里的参数顺序与条件顺序一致，逐个参数一行 */
    private static List<Map<String, Object>> flattenParams(List<Map<String, Object>> conditions) {
        List<Map<String, Object>> params = new ArrayList<>();
        for (Map<String, Object> condition : conditions) {
            params.addAll(castList(condition.get("params")));
        }
        return params;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private static Map<String, Object> queryParam(String name, String javaType, String pattern, Object desc,
                                                  String control, Object dictType, Object numberPrecision,
                                                  Object placeholder) {
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("name", name);
        param.put("javaType", javaType);
        param.put("tsType", tsType(javaType));
        param.put("jsonPattern", pattern);
        param.put("desc", String.valueOf(desc));
        param.put("descJs", jsText(String.valueOf(desc)));
        param.put("dictType", dictType);
        param.put("formControl", control);
        param.put("numberPrecision", numberPrecision);
        param.put("placeholder", placeholder);
        return param;
    }

    /**
     * 查询区控件：等值的字典列出下拉，日期列出日期框，数字列出数字框。
     * 区间拆出来的起止两个参数<b>不</b>按字典渲染 —— 日期区间用两个日期框，不是两个下拉。
     */
    private static String queryControl(String javaType, boolean hasDict) {
        if (hasDict) {
            return "dict";
        }
        return switch (javaType) {
            case "LocalDate" -> "date";
            case "LocalDateTime" -> "datetime";
            case "Integer", "Long", "BigDecimal", "Double", "Float" -> "number";
            default -> "input";
        };
    }

    /**
     * 列类型的完整写法：{@code varchar} + 长度 → {@code varchar(255)}，
     * {@code decimal} + 精度标度 → {@code decimal(18,2)}，其余原样。
     * 存库的是拆开的三段（{@code FieldType} 算出来的），生成 SQL 与实体注释要的是合起来的一份。
     *
     * <p>两条链路的入参形状不一样：低代码链路的 {@code lc_field.column_type} 存的已经是合好的一份
     * （{@code varchar(64)}），老代码生成页给的是 {@code information_schema} 的裸 {@code DATA_TYPE} 加长度列。
     * 已经带括号就原样收下来，否则这里会拼出 {@code varchar(64)(64)} —— 建表语句当场语法错误。
     */
    private static String columnType(Map<String, Object> field) {
        String base = blankToDefault(text(field.get("columnType")), "varchar").toLowerCase(Locale.ROOT);
        if (base.indexOf('(') >= 0) {
            return base;
        }
        Integer length = integer(field.get("columnLength"));
        Integer precision = integer(field.get("columnPrecision"));
        Integer scale = integer(field.get("columnScale"));
        if (("decimal".equals(base) || "numeric".equals(base)) && precision != null) {
            return base + "(" + precision + "," + (scale == null ? 0 : scale) + ")";
        }
        if (length != null && length > 0 && (base.startsWith("varchar") || base.startsWith("char"))) {
            return base + "(" + length + ")";
        }
        return base;
    }

    /** 小数列的标度：两条链路都算好存在库里（列长度 / 精度 / 标度），缺时按 {@code FieldType.DECIMAL} 的默认两位收 */
    private static Integer decimalScale(Map<String, Object> field) {
        Integer scale = integer(field.get("columnScale"));
        return scale != null && scale >= 0 ? scale : FieldType.DECIMAL.defaultScale();
    }

    private static Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = blankToNull(value == null ? null : String.valueOf(value));
        if (text == null) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 行数据里的布尔既可能是 Boolean，也可能是 MySQL 查出来的 0/1 */
    private static boolean truth(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = value == null ? null : String.valueOf(value);
        return text != null && ("1".equals(text) || "true".equalsIgnoreCase(text));
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /* ── 设计覆盖：把「形」换成设计里声明的那一份 ── */

    private static Map<String, Map<String, Object>> indexByCode(List<Map<String, Object>> items) {
        Map<String, Map<String, Object>> byCode = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            byCode.put(String.valueOf(item.get("code")), item);
        }
        return byCode;
    }

    /** 设计里的 field 一定对得上模型（PageDesign 保存时已逐格查过），对不上就是库里进了老数据 */
    private static Map<String, Object> copyOf(Map<String, Map<String, Object>> byCode, String code) {
        Map<String, Object> item = byCode.get(code);
        if (item == null) {
            throw new BusinessException("设计里绑的字段「" + code + "」在模型里已经不存在，请重新保存这份页面设计");
        }
        // 复制一份：同一列在查询区叫「状态」、在表头叫「订单状态」是合法的，不能改到模型那份上去
        return new LinkedHashMap<>(item);
    }

    private static List<Map<String, Object>> designQueryFields(List<DesignNode> nodes, Map<String, Map<String, Object>> byCode) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (DesignNode node : nodes) {
            Map<String, Object> item = copyOf(byCode, node.text("field"));
            rename(item, node.text("label"));
            item.put("placeholder", node.text("placeholder"));
            item.put("formControl", designControl(node.type(), item.get("dictType") != null, item));
            QueryType kind = QueryType.of(node.text("queryType"));
            item.put("queryKind", kind.name());
            item.put("queryOperator", kind.operator());
            out.add(item);
        }
        return out;
    }

    private static List<Map<String, Object>> designFormFields(List<DesignNode> nodes, Map<String, Map<String, Object>> byCode) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (DesignNode node : nodes) {
            Map<String, Object> item = copyOf(byCode, node.text("field"));
            rename(item, node.text("label"));
            item.put("placeholder", node.text("placeholder"));
            item.put("formControl", designControl(node.type(), item.get("dictType") != null, item));
            if (node.flag("required", false)) {
                item.put("inputRequired", true);
            }
            out.add(item);
        }
        return out;
    }

    private static List<Map<String, Object>> designColumns(List<DesignNode> nodes, Map<String, Map<String, Object>> byCode) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (DesignNode node : nodes) {
            Map<String, Object> item = copyOf(byCode, node.text("field"));
            rename(item, node.text("title"));
            item.put("render", node.text("render", deriveRender(item)));
            item.put("colWidth", node.number("width"));
            Integer minWidth = node.number("minWidth");
            item.put("colMinWidth", minWidth != null ? minWidth : defaultMinWidth(item));
            item.put("colAlign", node.text("align"));
            out.add(item);
        }
        return out;
    }

    /** 没有设计时列的形状回到类型推导，与 PR 5 的输出逐字节一致 */
    private static void normalizeColumns(List<Map<String, Object>> columns) {
        for (Map<String, Object> item : columns) {
            item.put("render", deriveRender(item));
            item.put("colWidth", null);
            item.put("colMinWidth", defaultMinWidth(item));
            item.put("colAlign", "number".equals(item.get("tsType")) ? "right" : null);
        }
    }

    private static int defaultMinWidth(Map<String, Object> item) {
        return "number".equals(item.get("tsType")) ? 110 : 160;
    }

    /** 单元格插槽由 render 决定：字典出标签、数字出等宽串、日期出弱化串，其余没有插槽 */
    private static String deriveRender(Map<String, Object> item) {
        if (item.get("dictType") != null) {
            return "tag";
        }
        if ("number".equals(item.get("tsType"))) {
            return "money";
        }
        String control = String.valueOf(item.get("formControl"));
        return "date".equals(control) || "datetime".equals(control) ? "datetime" : "text";
    }

    /** 设计控件 → 模板认的控件形态。DictSelect 没挂字典是设计的事（PageDesign 拦），这里只按类型映射 */
    private static String designControl(String type, boolean hasDict, Map<String, Object> item) {
        if (hasDict && "DictSelect".equals(type)) {
            return "dict";
        }
        return switch (type) {
            case "Textarea" -> "textarea";
            case "NumberInput" -> "number";
            case "DateInput" -> "LocalDateTime".equals(item.get("javaType")) ? "datetime" : "date";
            case "DateRangePicker" -> "range";
            case "Select" -> "select";
            default -> "input";
        };
    }

    private static void rename(Map<String, Object> item, String label) {
        if (label != null) {
            item.put("desc", label);
            item.put("descJs", jsText(label));
            item.put("inputVerb", isChoice(String.valueOf(item.get("formControl"))) ? "请选择" : "请输入");
        }
    }

    /** 写入语句的列 = 设计表单里摆了的 ∩ 模型允许写入的（两边都收紧，谁都不能单独放开） */
    private static List<Map<String, Object>> writableSubset(List<Map<String, Object>> formFields, boolean insert) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> item : formFields) {
            boolean autoKey = Boolean.TRUE.equals(item.get("primary")) && Boolean.TRUE.equals(item.get("autoIncrement"));
            if (insert) {
                if (Boolean.TRUE.equals(item.get("insert")) && !autoKey) {
                    out.add(item);
                }
            } else if (Boolean.TRUE.equals(item.get("edit")) && !Boolean.TRUE.equals(item.get("primary"))) {
                out.add(item);
            }
        }
        return out;
    }

    private static List<Map<String, Object>> uniqueSubset(List<Map<String, Object>> formFields) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> item : formFields) {
            if (Boolean.TRUE.equals(item.get("unique"))) {
                out.add(item);
            }
        }
        return out;
    }

    /** 主键不进列也要进 Row：openEdit 与 remove 都读它 */
    private static List<Map<String, Object>> withPrimary(Map<String, Object> primary, List<Map<String, Object>> columns) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (!columns.contains(primary)) {
            rows.add(primary);
        }
        rows.addAll(columns);
        return rows;
    }

    private static String blankToDefault(String value, String fallback) {
        String trimmed = blankToNull(value);
        return trimmed == null ? fallback : trimmed;
    }
}

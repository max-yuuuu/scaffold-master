package com.scaffold.lowcode.design;

import java.util.List;
import java.util.Set;

/**
 * 设计器第一期的组件、动作与属性白名单（方案 §3.3、§3.4）。
 *
 * <p>白名单<b>只收生成器第一期真产得出来的东西</b>。方案 §3.3 的组件表里还有部门选择、用户选择、
 * 上传、开关、读数，动作表里还有 {@code view}（跳详情）与 {@code custom:{name}}：它们的取数来源
 * （部门树、用户列表、上传接口、详情页、前端 overrides/）不由模型推导或还没定稿，
 * 收进白名单就等于允许人搭出一个生成不出来的页面 —— 那比报错糟得多。
 *
 * <p>每种组件登记的属性是「设计里能出现什么」，不是「生成物里有什么」。属性之外的形状由模型给：
 * 字典挂在 {@code lc_field.dict_type}，小数位挂在 {@code column_scale}，控件类型默认由 {@code form_type} 推。
 * 这些地方留两份事实，下一次改动只会改到一边。
 */
public final class ComponentCatalog {

    /** 节点类型：容器能装子节点，控件与按钮只能待在指定容器里，列是 DataTable 专属 */
    public enum Kind {
        PAGE, CONTAINER, CONTROL, BUTTON
    }

    /** 一个组件的档案：中文名、种类、能待在哪些容器里、支持哪些属性 */
    public record Entry(String type, String label, Kind kind, List<String> parents, Set<String> props) {
    }

    /** 列表页的五个区块（PageContainer 之外唯一允许出现在页面直属层的类型） */
    public static final String PAGE_CONTAINER = "PageContainer";
    public static final String SEARCH = "SearchForm";
    public static final String TOOLBAR = "Toolbar";
    public static final String TABLE = "DataTable";
    public static final String ROW_ACTIONS = "RowActions";
    public static final String FORM = "FormContainer";

    /**
     * 内置动作。每个动作对应生成物里一处确定的接线，权限点也由它推导（{@link #permissionSuffix(String)}）：
     * 让人手写权限串，就会出现「菜单里权限点齐着、页面上按钮永远灰」这种查起来很费劲的不一致。
     */
    private static final Set<String> ACTIONS = Set.of("create", "edit", "delete", "export", "refresh");

    /** 认识但第一期不产出的动作，报错时要能说明原因，别让人以为是写错了 */
    private static final Set<String> DEFERRED_ACTIONS = Set.of("view");

    /** 表格列的渲染方式，与 ListView 模板里的单元格插槽一一对应 */
    private static final Set<String> RENDERS = Set.of("text", "money", "tag", "datetime");

    /** 列对齐，取值与物料 {@code Column.align} 一致 */
    private static final Set<String> ALIGNS = Set.of("left", "right", "center");

    private static final List<Entry> ENTRIES = List.of(
            new Entry(PAGE_CONTAINER, "页面", Kind.PAGE, List.of(), Set.of("title", "desc")),
            new Entry(SEARCH, "查询区", Kind.CONTAINER, List.of(PAGE_CONTAINER), Set.of("columns", "gap")),
            new Entry(TOOLBAR, "工具栏", Kind.CONTAINER, List.of(PAGE_CONTAINER), Set.of()),
            new Entry(TABLE, "表格区", Kind.CONTAINER, List.of(PAGE_CONTAINER), Set.of("pagination", "pageSize")),
            new Entry(ROW_ACTIONS, "行操作", Kind.CONTAINER, List.of(PAGE_CONTAINER), Set.of("priority")),
            new Entry(FORM, "表单弹窗", Kind.CONTAINER, List.of(PAGE_CONTAINER), Set.of("columns")),

            new Entry("TextInput", "文本输入", Kind.CONTROL, List.of(SEARCH, FORM),
                    Set.of("label", "field", "required", "placeholder", "queryType")),
            new Entry("Textarea", "多行文本", Kind.CONTROL, List.of(FORM),
                    Set.of("label", "field", "required", "placeholder")),
            new Entry("NumberInput", "数字输入", Kind.CONTROL, List.of(SEARCH, FORM),
                    Set.of("label", "field", "required", "placeholder", "queryType")),
            new Entry("DateInput", "日期选择", Kind.CONTROL, List.of(SEARCH, FORM),
                    Set.of("label", "field", "required", "queryType")),
            new Entry("DateRangePicker", "日期区间", Kind.CONTROL, List.of(SEARCH),
                    Set.of("label", "field", "queryType")),
            new Entry("Select", "下拉选择", Kind.CONTROL, List.of(SEARCH, FORM),
                    Set.of("label", "field", "required", "placeholder", "queryType")),
            new Entry("DictSelect", "字典下拉", Kind.CONTROL, List.of(SEARCH, FORM),
                    Set.of("label", "field", "required", "placeholder", "queryType")),

            new Entry("Button", "按钮", Kind.BUTTON, List.of(TOOLBAR, ROW_ACTIONS),
                    Set.of("label", "action", "variant", "danger")));

    private static final List<String> ALL_TYPES = ENTRIES.stream().map(Entry::type).toList();
    private static final List<String> CONTAINER_TYPES = ENTRIES.stream()
            .filter(entry -> entry.kind() == Kind.CONTAINER)
            .map(Entry::type)
            .toList();

    /** DataTable 的列对象支持的属性：不是组件，所以单独一套 */
    public static final Set<String> COLUMN_PROPS = Set.of("field", "title", "width", "minWidth", "align", "sortable", "render");

    /** 一份设计的上限：节点数与容器层数。画布上点不到的深层嵌套与几百格页面都没有出口 */
    public static final int MAX_NODES = 200;
    public static final int MAX_CONTAINER_DEPTH = 2;

    private ComponentCatalog() {
    }

    public static Entry of(String type) {
        return ENTRIES.stream().filter(entry -> entry.type().equals(type)).findFirst().orElse(null);
    }

    public static boolean isKnown(String type) {
        return of(type) != null;
    }

    /** 报错要能指着人看得懂的清单说「第一期支持这些」，而不是只说「不认识 Carousel」 */
    public static List<String> supportedTypes() {
        return ALL_TYPES;
    }

    public static List<String> containerTypes() {
        return CONTAINER_TYPES;
    }

    public static Set<String> actions() {
        return ACTIONS;
    }

    public static boolean isDeferredAction(String action) {
        return DEFERRED_ACTIONS.contains(action);
    }

    public static Set<String> renders() {
        return RENDERS;
    }

    public static Set<String> aligns() {
        return ALIGNS;
    }

    /**
     * 动作 → 生成的菜单种子里的权限点后缀。口径与 {@code GenerationInput.MENU_BUTTONS} 一致：
     * 列表与导出共用 {@code :list}，与底座的 {@code system:post:list} 同一写法。
     */
    public static String permissionSuffix(String action) {
        return switch (action) {
            case "create" -> "add";
            case "edit" -> "edit";
            case "delete" -> "remove";
            default -> "list";
        };
    }

    /** 模型侧独有事实与「看着能用其实会打架」的属性，单独给一句话，别混进通用报错 */
    public static String dedicatedPropMessage(String key) {
        return switch (key) {
            case "dictType" -> "字典挂在字段上（模型里已经选了 dict_type），设计里不要再写一份 dictType —— "
                    + "两份事实迟早打架，而打架的表现是列表与表单翻译出不同的取值";
            case "precision" -> "小数位由模型的 decimal 标度给（lc_field.column_scale），不接受设计里手写 precision —— "
                    + "5G 演练里金额 1234.5 被静默取整成 1235 就是这么来的";
            case "permission" -> "权限点由动作推导（create→add、edit→edit、delete→remove、export 与 refresh→list），"
                    + "不接受手写 permission：手写的那串与生成的菜单种子对不上时，表现是菜单里权限点齐着、页面上按钮永远灰";
            default -> null;
        };
    }
}

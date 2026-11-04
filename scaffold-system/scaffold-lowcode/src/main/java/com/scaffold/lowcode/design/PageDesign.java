package com.scaffold.lowcode.design;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.lowcode.model.QueryType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 设计器产出的一份页面配置（方案 §3.4）。
 *
 * <p>这一份是 PR 6 的全部交界面：设计器只管往 JSON 里写「形」，生成器只管从模型里取「料」，
 * 中间靠 {@link #parse} 把两者对得上。校验规则每条都在拦一类「编得过、单测绿、真机才炸」的接缝缺陷
 * （见 changelog 里 5G 那六条），所以报错必须说清<em>为什么</em>不让过，光说「不合法」等于让人再猜一轮。
 *
 * <p>结构借 LCE 搭建协议的形状（{@code layout} 树 + 组件 type / props / children），
 * <b>不</b>接全协议：本项目走代码生成路线，没有生产运行时要渲染这份 JSON（§10.2 第 4 项）。
 */
public final class PageDesign {

    /** 本期唯一认的协议版本。版本不认识就当场拒绝，别用「宽容解析」把一个错形状的设计塞进库 */
    public static final String SUPPORTED_VERSION = "1.0";
    public static final String PAGE_TYPE_LIST = "list";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String version;
    private final String pageType;
    private final String modelRef;
    private final DesignNode root;
    private final List<String> referencedFields;

    private PageDesign(String version, String pageType, String modelRef, DesignNode root, List<String> referencedFields) {
        this.version = version;
        this.pageType = pageType;
        this.modelRef = modelRef;
        this.root = root;
        this.referencedFields = List.copyOf(referencedFields);
    }

    /**
     * 解析并校验一份设计。
     *
     * @param json design_json 原文
     * @param ctx  页面绑定模型的事实（字段编码、类型、字典、可写性）
     * @throws BusinessException 任何一处对不上都当场拒绝，消息带着节点路径
     */
    public static PageDesign parse(String json, DesignContext ctx) {
        if (json == null || json.isBlank()) {
            throw new BusinessException("design_json 不能为空：页面至少要声明 layout 与绑定的模型");
        }
        JsonNode doc;
        try {
            doc = MAPPER.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("design_json 不是合法的 JSON，无法保存：" + ex.getOriginalMessage());
        }
        if (doc == null || !doc.isObject()) {
            throw new BusinessException("design_json 顶层必须是对象（version / pageType / modelRef / layout）");
        }

        String version = valueOf(doc.get("version"));
        if (!SUPPORTED_VERSION.equals(version)) {
            throw new BusinessException("version 只支持 \"" + SUPPORTED_VERSION + "\"，这份设计里是 "
                    + (version == null ? "（没写）" : "\"" + version + "\""));
        }
        String pageType = valueOf(doc.get("pageType"));
        if (pageType == null) {
            throw new BusinessException("pageType 不能为空：列表页写 \"" + PAGE_TYPE_LIST + "\"");
        }
        if (!PAGE_TYPE_LIST.equals(pageType)) {
            throw new BusinessException("pageType「" + pageType + "」第一期只支持列表页（list）；"
                    + "详情 / 树表 / 看板排在后面，现在生成器里没有对应的模板");
        }
        String modelRef = valueOf(doc.get("modelRef"));
        if (modelRef == null) {
            throw new BusinessException("modelRef 不能为空：页面与模型的耦合只在这一个符号上（§1.5）");
        }
        if (!modelRef.equals(ctx.modelRef())) {
            throw new BusinessException("modelRef「" + modelRef + "」与这份页面归属的模型「" + ctx.modelRef()
                    + "」不一致：设计不能跨模型复制，字段清单对不上");
        }
        JsonNode layout = doc.get("layout");
        if (layout == null || layout.isNull()) {
            throw new BusinessException("layout 不能为空：页面的区块树写在 layout 里");
        }
        if (!layout.isObject()) {
            throw new BusinessException("layout 必须是 PageContainer 对象，收到的是 " + layout.getNodeType());
        }

        guardScale(layout);
        DesignNode root = build(layout, "layout");
        if (!ComponentCatalog.PAGE_CONTAINER.equals(root.type())) {
            throw new BusinessException("layout 必须是 PageContainer，收到「" + root.type() + "」");
        }
        return validate(root, ctx, version, pageType, modelRef);
    }

    /* ── 对外读数：生成器与设计器都从这里取，不再回去翻 JSON ── */

    public String version() {
        return version;
    }

    public String pageType() {
        return pageType;
    }

    public String modelRef() {
        return modelRef;
    }

    /** 页面标题（画布最顶上那一行，也是生成菜单里的菜单名） */
    public String title() {
        return root.text("title", modelRef);
    }

    /** 页面说明，对应 ListPage 的 desc */
    public String description() {
        return root.text("desc", null);
    }

    /** 页面直属的区块，顺序就是画布上从上到下 */
    public List<DesignNode> blocks() {
        return root.children();
    }

    /** 表格区。{@link #parse} 已经保证列表页必有，所以这里不会为 null */
    public DesignNode table() {
        return find(ComponentCatalog.TABLE);
    }

    public List<DesignNode> search() {
        return childrenOf(ComponentCatalog.SEARCH);
    }

    public List<DesignNode> toolbar() {
        return childrenOf(ComponentCatalog.TOOLBAR);
    }

    public List<DesignNode> rowActions() {
        return childrenOf(ComponentCatalog.ROW_ACTIONS);
    }

    public List<DesignNode> form() {
        return childrenOf(ComponentCatalog.FORM);
    }

    public List<DesignNode> columns() {
        return table().columns();
    }

    /**
     * 这份设计引用了哪些字段编码，去重后按出现顺序。
     *
     * <p>保存时写进 {@code lc_page.ref_fields}：删字段 / 下线模型前按它反查影响面（§1.5），
     * 而不是等发布时模板报错才发现，也不做 JSON 全文搜索 —— 全文搜索会把「orderNo 出现在某句说明里」
     * 当成引用，反过来也会漏掉。
     */
    public List<String> referencedFields() {
        return referencedFields;
    }

    public DesignNode find(String blockType) {
        return root.children().stream().filter(node -> node.type().equals(blockType)).findFirst().orElse(null);
    }

    public List<DesignNode> childrenOf(String blockType) {
        DesignNode block = find(blockType);
        return block == null ? List.of() : block.children();
    }

    /* ── 校验 ── */

    private static PageDesign validate(DesignNode root, DesignContext ctx, String version, String pageType, String modelRef) {
        for (DesignNode block : root.children()) {
            ComponentCatalog.Entry entry = ComponentCatalog.of(block.type());
            if (entry == null) {
                throw unsupported(block);
            }
            if (entry.kind() != ComponentCatalog.Kind.CONTAINER) {
                throw new BusinessException(block.path() + "：PageContainer 下只能放容器，收到「" + block.type() + "」。"
                        + "控件要先进容器 —— 查询条件进 " + ComponentCatalog.SEARCH + "，表单字段进 "
                        + ComponentCatalog.FORM + "，按钮进 " + ComponentCatalog.TOOLBAR + " / "
                        + ComponentCatalog.ROW_ACTIONS + "，列进 " + ComponentCatalog.TABLE);
            }
        }
        if (root.children().stream().noneMatch(node -> node.type().equals(ComponentCatalog.TABLE))) {
            throw new BusinessException("页面必须有 DataTable：列表页没有表格就不成其为列表页");
        }

        Set<String> refs = new LinkedHashSet<>();
        Walk walk = new Walk();
        validateProps(root, ComponentCatalog.of(ComponentCatalog.PAGE_CONTAINER).props());
        for (DesignNode block : root.children()) {
            validateProps(block, block.entry().props());
            if (ComponentCatalog.FORM.equals(block.type())) {
                walk.hasForm = true;
            }
            if (ComponentCatalog.TABLE.equals(block.type())) {
                validateTable(block, ctx, refs);
                continue;
            }
            validateChildren(block, ctx, refs, walk);
        }
        requireFormForWriteActions(walk);
        requireFormCoversDemandingFields(walk, ctx);
        return new PageDesign(version, pageType, modelRef, root, List.copyOf(refs));
    }

    /** 走一遍设计顺带收集的三件事：表单里摆了哪些字段、出现了哪些写动作、有没有表单弹窗 */
    private static final class Walk {
        private final Set<String> formFields = new LinkedHashSet<>();
        private final Set<String> writeActions = new LinkedHashSet<>();
        private boolean hasForm;
    }

    /**
     * 有新增或编辑按钮就必须有弹窗：按钮点开是一台空对话框，人在上面填不到任何东西，
     * 但页面看着是「能新增」的 —— 这种假接口比缺按钮更难被发现。
     */
    private static void requireFormForWriteActions(Walk walk) {
        if (walk.hasForm || walk.writeActions.isEmpty()) {
            return;
        }
        throw new BusinessException("动作 " + String.join("、", walk.writeActions)
                + " 要求页面画了 FormContainer：没有弹窗，新增与编辑按钮点开是空的。"
                + "只做只读台账就把这些按钮删掉");
    }

    /**
     * 画了弹窗就要收齐「必须由人给值」的列。
     *
     * <p>表单不收这一格，生成的 INSERT 就没有这一列；列又是 NOT NULL 且没默认值，
     * 新增点下去是运行期 500 —— 页面上一眼看不出问题，只有数据库知道。判据与建表语句同源
     * （{@link DesignContext.Field#demanding()}），两处不同口径这条规则自己就会打架。
     */
    private static void requireFormCoversDemandingFields(Walk walk, DesignContext ctx) {
        if (!walk.hasForm) {
            return;
        }
        List<DesignContext.Field> missing = ctx.demandingFields().stream()
                .filter(field -> !walk.formFields.contains(field.code()))
                .toList();
        if (missing.isEmpty()) {
            return;
        }
        String names = missing.stream().map(field -> field.code() + "（" + field.name() + "）").toList().toString();
        throw new BusinessException("这些列必须由人给值，必须出现在表单里：" + names
                + " —— 它们 NOT NULL 又没有默认值，表单不收就等于新增时少一列，点保存是运行期 500。"
                + "要么摆进表单，要么回模型里给它默认值或允许为空");
    }

    /** 区块里的子组件：种类要对、属性要对，绑的字段必须是模型真实字段 */
    private static void validateChildren(DesignNode block, DesignContext ctx, Set<String> refs, Walk walk) {
        List<String> allowed = allowedIn(block.type());
        Set<String> seen = new LinkedHashSet<>();
        for (DesignNode item : block.children()) {
            ComponentCatalog.Entry entry = ComponentCatalog.of(item.type());
            if (entry == null) {
                throw unsupported(item);
            }
            if (!entry.parents().contains(block.type())) {
                throw placement(item, block.type(), allowed);
            }
            validateProps(item, entry.props());
            if (entry.kind() == ComponentCatalog.Kind.BUTTON) {
                validateButton(item, walk);
                continue;
            }
            DesignContext.Field field = requireField(item, ctx);
            // 同一个区块里绑两遍同一个字段：表单类型会重复声明一个成员（vue-tsc 当场报错），
            // 查询区则是一个条件出现两个框，人只会改其中一个
            if (!seen.add(field.code())) {
                throw error(item.path(), entry.label() + "「" + field.name() + "」在这个区块里重复绑定 "
                        + field.code() + "：一个区块里同一个字段只出现一次");
            }
            validateControlType(item, field);
            if (ComponentCatalog.SEARCH.equals(block.type())) {
                validateQuery(item, field);
            } else {
                validateWritable(item, field);
                walk.formFields.add(field.code());
            }
            refs.add(field.code());
        }
    }

    private static void validateButton(DesignNode node, Walk walk) {
        if (node.text("label") == null) {
            throw error(node.path(), "按钮必须给出 label：页面上是一个没有字的按钮，人不知道点下去做什么");
        }
        String action = node.text("action");
        if (action == null) {
            throw error(node.path(), "按钮必须选一个动作（action）。第一期内置动作：" + actions());
        }
        if (action.startsWith("custom:")) {
            throw error(node.path(), "自定义动作「" + action + "」第一期不产出：它要在交付项目的 overrides/ 里"
                    + "落一个带 TODO 的 handler 桩，而覆写怎么被发现、盖到 B 档页面的哪个区块上、整页接管怎么退出"
                    + "这三个问题还没定稿（方案 §10.2 第 3 项）");
        }
        if (ComponentCatalog.isDeferredAction(action)) {
            throw error(node.path(), "动作「" + action + "」第一期不产出：详情页模板还没进生成器，"
                    + "放一个指向不存在路由的按钮比少一个按钮更糟");
        }
        if (!ComponentCatalog.actions().contains(action)) {
            throw error(node.path(), "未知的动作 " + action + "。第一期内置动作：" + actions()
                    + "；权限点由动作推导，不需要也不能自己编一个");
        }
        if ("create".equals(action) || "edit".equals(action)) {
            walk.writeActions.add(action);
        }
    }

    /** 控件绑的字段必须是模型真实字段 —— 属性面板的下拉本来就只能从模型字段里选 */
    private static DesignContext.Field requireField(DesignNode node, DesignContext ctx) {
        String code = node.text("field");
        if (code == null) {
            throw error(node.path(), node.type() + " 必须绑定字段：没有取数来源的控件在页面上只能显示空白");
        }
        DesignContext.Field field = ctx.find(code);
        if (field == null) {
            throw error(node.path(), "字段「" + code + "」不是模型 " + ctx.modelRef() + " 的字段。可用字段："
                    + ctx.fieldCodes() + " —— 要加字段请先回到模型管理里建，设计器不代替模型造字段");
        }
        return field;
    }

    /** 控件种类要和字段类型配得上：文本框绑日期列就是收进 {@code 2026/11/4} 这种后端收不下的写法 */
    private static void validateControlType(DesignNode node, DesignContext.Field field) {
        switch (node.type()) {
            case "DateInput", "DateRangePicker" -> {
                if (!field.dateLike()) {
                    throw error(node.path(), node.type() + " 只能绑日期字段，「" + field.name() + "」是 "
                            + field.javaType());
                }
            }
            case "NumberInput" -> {
                if (!field.numeric()) {
                    throw error(node.path(), "NumberInput 只能绑数字字段，「" + field.name() + "」是 " + field.javaType());
                }
            }
            default -> {
                if (field.dateLike()) {
                    throw error(node.path(), "日期字段「" + field.name() + "」要用 DateInput（区间用 DateRangePicker），"
                            + "用 " + node.type() + " 会收进后端解析不了的日期写法");
                }
            }
        }
    }

    /** 查询区：查询方式要和字段类型配得上，区间只在日期与数字上成立 */
    private static void validateQuery(DesignNode node, DesignContext.Field field) {
        if (node.props().containsKey("required")) {
            throw error(node.path(), "查询条件没有必填：查询区留空就是「不限制」，配 required 只会让人以为查不出数据");
        }
        String raw = node.text("queryType", QueryType.EQ.operator());
        QueryType kind = parseQueryType(node, raw);
        if ("DateRangePicker".equals(node.type()) && kind != QueryType.BETWEEN) {
            throw error(node.path(), "DateRangePicker 只能配 BETWEEN：区间控件的两个端点分别进 "
                    + field.code() + "Start / " + field.code() + "End 两个参数，配 " + raw + " 没处放");
        }
        if (kind == QueryType.BETWEEN && !(field.numeric() || field.dateLike())) {
            throw error(node.path(), "BETWEEN 只给日期与数字字段：区间会拆成两个参数逐个比较，"
                    + "「" + field.name() + "」是 " + field.javaType() + "，比较出来不是人要的结果");
        }
        if (kind == QueryType.LIKE && !"String".equals(field.javaType())) {
            throw error(node.path(), "LIKE 只对文本字段有意义：「" + field.name() + "」是 " + field.javaType()
                    + "，让人打 12 去匹配 12.00 之外，前置通配还用不上索引");
        }
    }

    /** 表单区：字段得真的写得进去，自增主键不写进去 */
    private static void validateWritable(DesignNode node, DesignContext.Field field) {
        if (field.autoKey()) {
            throw error(node.path(), "自增主键「" + field.name() + "」不进表单：值由数据库给，收进来等于让人猜下一个 id，"
                    + "而且页面的表单类型会为它重复声明一个成员，交付项目 vue-tsc 当场报错（5G 的真机缺陷）");
        }
        if (!field.writable()) {
            throw error(node.path(), "字段「" + field.name() + "」在模型里不允许写入（可录入与可编辑都关着），"
                    + "表单收了也是填完就丢 —— 生成的 INSERT / UPDATE 里没有这一列");
        }
    }

    private static void validateTable(DesignNode block, DesignContext ctx, Set<String> refs) {
        Set<String> seen = new LinkedHashSet<>();
        if (block.columns().isEmpty()) {
            throw error(block.path(), "DataTable 至少要声明一列：一列都没有的表格只剩「操作」列");
        }
        for (DesignNode column : block.columns()) {
            for (String key : column.props().keySet()) {
                if (!ComponentCatalog.COLUMN_PROPS.contains(key)) {
                    throw error(column.path(), "列不支持属性「" + key + "」。一列支持：" + sorted(ComponentCatalog.COLUMN_PROPS));
                }
            }
            DesignContext.Field field = requireField(column, ctx);
            if (!seen.add(field.code())) {
                throw error(column.path(), "表格里重复声明了「" + field.name() + "」这一列");
            }
            validateAlign(column);
            validateWidth(column, "width");
            validateWidth(column, "minWidth");
            validateRender(column, field);
            refs.add(field.code());
        }
    }

    private static void validateAlign(DesignNode column) {
        String align = column.text("align");
        if (align != null && !ComponentCatalog.aligns().contains(align)) {
            throw error(column.path(), "align「" + align + "」只能是 " + sorted(ComponentCatalog.aligns())
                    + "，与物料 Column.align 的取值一致");
        }
    }

    private static void validateWidth(DesignNode column, String key) {
        if (!column.props().containsKey(key)) {
            return;
        }
        Integer width = column.number(key);
        if (width == null || width < 0) {
            throw error(column.path(), key + " 要是一个不小于 0 的整数（像素），收到「" + column.props().get(key) + "」");
        }
    }

    /** 列的渲染方式决定生成哪个单元格插槽，跟字段类型对不上就是页面上一片空白 */
    private static void validateRender(DesignNode column, DesignContext.Field field) {
        String render = column.text("render");
        if (render == null) {
            return;
        }
        if (!ComponentCatalog.renders().contains(render)) {
            throw error(column.path(), "未知的列渲染 " + render + "。可用：" + sorted(ComponentCatalog.renders()));
        }
        if ("money".equals(render) && !field.numeric()) {
            throw error(column.path(), "render=money 只能给数字字段：「" + field.name() + "」是 "
                    + field.javaType() + "，按金额格式化出来是空");
        }
        if ("datetime".equals(render) && !field.dateLike()) {
            throw error(column.path(), "render=datetime 只能给日期字段：「" + field.name() + "」是 " + field.javaType());
        }
        if ("tag".equals(render) && !field.hasDict()) {
            throw error(column.path(), "render=tag 要按字典翻译取值：字段「" + field.name()
                    + "」没挂字典，标签里是一片空白 —— 先在模型里给它选上字典");
        }
    }

    /** 属性名逐个查：不认识就报，并把该组件支持的属性列出来。设计器与校验器同时改，才不会有第三种形状 */
    private static void validateProps(DesignNode node, Set<String> supported) {
        for (String key : node.props().keySet()) {
            if (supported.contains(key)) {
                continue;
            }
            String dedicated = ComponentCatalog.dedicatedPropMessage(key);
            if (dedicated != null) {
                throw error(node.path(), dedicated);
            }
            throw error(node.path(), node.type() + " 不支持属性「" + key + "」。它支持："
                    + sorted(supported) + "（值不在这里的形状，由模型给：类型、字典、小数位、控件类型都认 lc_field）");
        }
    }

    private static BusinessException unsupported(DesignNode node) {
        return error(node.path(), "不支持的组件 " + node.type() + "。第一期支持："
                + String.join("、", ComponentCatalog.supportedTypes()));
    }

    private static BusinessException placement(DesignNode node, String blockType, List<String> allowed) {
        if (allowed.size() == 1) {
            return error(node.path(), blockType + " 里只能放 " + allowed.get(0) + "，收到 " + node.type());
        }
        return error(node.path(), blockType + " 里不能放 " + node.type() + "：它能待在的容器是 "
                + String.join(" / ", node.entry().parents()));
    }

    private static List<String> allowedIn(String blockType) {
        return ComponentCatalog.supportedTypes().stream()
                .map(ComponentCatalog::of)
                .filter(entry -> entry != null && entry.parents().contains(blockType))
                .map(ComponentCatalog.Entry::type)
                .toList();
    }

    /** 查询方式：只认 {@link QueryType} 的枚举名或运算符，认不了就报，不静默按等于处理 */
    private static QueryType parseQueryType(DesignNode node, String raw) {
        for (QueryType type : QueryType.values()) {
            if (type.name().equalsIgnoreCase(raw) || type.operator().equals(raw)) {
                return type;
            }
        }
        throw error(node.path(), "未知的查询方式「" + raw + "」。可用："
                + String.join(" / ", QueryType.codes()));
    }

    /**
     * 规模守卫：先数完节点与层数再开始校验。
     *
     * <p>放在最前面是因为这两条拦的是「这份 JSON 大到没法当页面用」，逐节点校验走到一半才发现
     * 超规模，等于让人对着一个第 300 格的报错去数自己画了多少格。
     */
    private static void guardScale(JsonNode layout) {
        int[] nodes = {1};
        // 层数从 PageContainer 自己算起：它算第一层，区块容器第二层，控件不再往下套
        int depth = containerDepth(layout, 1, nodes);
        if (nodes[0] > ComponentCatalog.MAX_NODES) {
            throw new BusinessException("设计的节点数 " + nodes[0] + " 超过上限 " + ComponentCatalog.MAX_NODES
                    + "：一个页面画到几百格就不是给人用的页面了");
        }
        if (depth > ComponentCatalog.MAX_CONTAINER_DEPTH) {
            throw new BusinessException("容器最多两层（PageContainer > 容器 > 控件），这份设计套了 " + depth
                    + " 层：再深一格的方块在画布上点不到，生成物里也没有地方摆它");
        }
    }

    private static int containerDepth(JsonNode node, int current, int[] nodes) {
        int max = current;
        JsonNode children = node.get("children");
        if (children != null && children.isArray()) {
            for (JsonNode child : children) {
                nodes[0]++;
                if (!child.isObject()) {
                    continue;
                }
                String type = valueOf(child.get("type"));
                ComponentCatalog.Entry entry = type == null ? null : ComponentCatalog.of(type);
                if (entry != null && entry.kind() == ComponentCatalog.Kind.CONTAINER) {
                    max = Math.max(max, containerDepth(child, current + 1, nodes));
                } else {
                    containerDepth(child, current, nodes);
                }
            }
        }
        JsonNode columns = node.get("columns");
        if (columns != null && columns.isArray()) {
            nodes[0] += columns.size();
        }
        return max;
    }

    /* ── JSON → DesignNode ── */

    private static DesignNode build(JsonNode json, String path) {
        if (!json.isObject()) {
            throw error(path, "这里必须是一个对象（type / props / children），收到的是 " + json.getNodeType());
        }
        String type = valueOf(json.get("type"));
        if (type == null) {
            throw error(path, "组件必须写明 type（第一期支持：" + String.join("、", ComponentCatalog.supportedTypes()) + "）");
        }
        if (ComponentCatalog.TABLE.equals(type) && json.path("children").isArray() && json.path("children").size() > 0) {
            throw error(path, "DataTable 只用 columns 声明列，不接受 children：列不是组件，"
                    + "写成 children 生成器就得猜这是列还是控件");
        }
        Map<String, Object> props = props(json, path);
        List<DesignNode> children = new ArrayList<>();
        JsonNode childNodes = json.get("children");
        if (childNodes != null && childNodes.isArray()) {
            for (int i = 0; i < childNodes.size(); i++) {
                children.add(build(childNodes.get(i), path + ".children[" + i + "]"));
            }
        }
        List<DesignNode> columns = new ArrayList<>();
        JsonNode columnNodes = json.get("columns");
        if (columnNodes != null && columnNodes.isArray()) {
            for (int i = 0; i < columnNodes.size(); i++) {
                JsonNode column = columnNodes.get(i);
                String columnPath = path + ".columns[" + i + "]";
                if (!column.isObject()) {
                    throw error(columnPath, "列声明必须是一个对象（field / title / width / align / render）");
                }
                columns.add(new DesignNode(null, columnPath, flatProps(column), List.of(), List.of()));
            }
        }
        return new DesignNode(type, path, props, children, columns);
    }

    /**
     * 组件的 {@code props} 表。列对象没有这一层，属性直接平铺在列上，所以另有 {@link #flatProps}。
     *
     * @param path 报错定位用：属性写错时要说清是哪一格
     */
    private static Map<String, Object> props(JsonNode json, String path) {
        JsonNode node = json.get("props");
        if (node == null || node.isNull()) {
            return new LinkedHashMap<>();
        }
        if (!node.isObject()) {
            throw error(path, "props 必须是一个对象（属性的属性），收到的是 " + node.getNodeType());
        }
        return flatProps(node);
    }

    private static Map<String, Object> flatProps(JsonNode node) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            String key = it.next();
            JsonNode value = node.get(key);
            out.put(key, value.isNull() ? null : scalar(value));
        }
        return out;
    }

    private static Object scalar(JsonNode value) {
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        if (value.isIntegralNumber()) {
            return value.intValue();
        }
        if (value.isNumber()) {
            return value.doubleValue();
        }
        if (value.isTextual()) {
            return value.textValue();
        }
        return value.toString();
    }

    private static String valueOf(JsonNode node) {
        if (node == null || node.isNull() || !node.isValueNode()) {
            return null;
        }
        String text = node.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private static BusinessException error(String path, String message) {
        return new BusinessException(path + "：" + message);
    }

    private static String actions() {
        return String.join(" / ", sorted(ComponentCatalog.actions()));
    }

    private static List<String> sorted(Set<String> values) {
        return values.stream().sorted().toList();
    }
}

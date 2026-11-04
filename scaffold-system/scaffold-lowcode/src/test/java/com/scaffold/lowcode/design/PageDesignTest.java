package com.scaffold.lowcode.design;

import com.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * design_json 的解析与校验（PR 6）。
 *
 * <p>结构借方案 §3.4 的搭建协议形状，<b>不</b>接 LCE 全协议（§10.2 第 4 项）：本项目走代码生成路线，
 * 画布只需要设计态近似预览，没有生产运行时来渲染这份 JSON。
 *
 * <p>校验只有一条准绳：放过去的错都要等到真机才炸。5G 演练撞出来的六处接缝缺陷里，「自增主键进表单」
 * 「金额被静默取整」「列表列取不到数」三处都属于<b>页面声明与模型事实不一致</b>，而当时类型检查与单测全绿 ——
 * 所以这一层拦的就是不一致，不是 JSON 语法好不好看。
 *
 * <p>报错一律带节点路径（{@code layout.children[0].children[1]}），设计器属性面板照着它把画布上那个方块
 * 高亮出来；只说「字段不对」等于让人在一堆方块里自己找。
 */
class PageDesignTest {

    /* ── 一份完整设计：五个区块齐、字段与类型都对 —— 各用例只改自己那一条 ── */

    private static final String SEARCH = search(
            control("TextInput", "label=订单编号", "field=orderNo", "queryType=LIKE"),
            control("DictSelect", "label=状态", "field=status"));
    private static final String TOOLBAR = toolbar(
            control("Button", "label=新增", "action=create", "variant=primary"),
            control("Button", "label=导出", "action=export"));
    private static final String TABLE = table(
            column("orderNo", "订单编号", "width=160"),
            column("amount", "订单金额", "align=right", "render=money"),
            column("status", "状态", "render=tag"),
            column("deliveryDate", "交货日期", "render=datetime"));
    private static final String ROW_ACTIONS = rowActions(
            control("Button", "label=编辑", "action=edit"),
            control("Button", "label=删除", "action=delete", "danger=true"));
    private static final String FORM = form(
            control("TextInput", "label=订单编号", "field=orderNo", "required=true"),
            control("NumberInput", "label=订单金额", "field=amount", "required=true"),
            control("DictSelect", "label=状态", "field=status"),
            control("DateInput", "label=交货日期", "field=deliveryDate"),
            control("Textarea", "label=备注", "field=remark"));

    private static String fullPage() {
        return page(SEARCH, TOOLBAR, TABLE, ROW_ACTIONS, FORM);
    }

    @Test
    void 一份列表页设计解析出区块顺序与引用字段() {
        PageDesign design = PageDesign.parse(fullPage(), context());

        assertThat(design.version()).isEqualTo("1.0");
        assertThat(design.pageType()).isEqualTo("list");
        assertThat(design.modelRef()).isEqualTo("sales_order");
        assertThat(design.title()).isEqualTo("销售订单");
        assertThat(design.description()).isEqualTo("管理所有销售订单");
        // 区块顺序就是画布上的上下顺序，生成页面的排版按它走
        assertThat(design.blocks()).extracting(DesignNode::type)
                .containsExactly("SearchForm", "Toolbar", "DataTable", "RowActions", "FormContainer");
        assertThat(design.search()).extracting(node -> node.text("field")).containsExactly("orderNo", "status");
        assertThat(design.table().columns()).extracting(node -> node.text("field"))
                .containsExactly("orderNo", "amount", "status", "deliveryDate");
        assertThat(design.form()).hasSize(5);
        assertThat(design.toolbar()).extracting(node -> node.text("action")).containsExactly("create", "export");
        assertThat(design.rowActions()).extracting(node -> node.text("action")).containsExactly("edit", "delete");
        assertThat(design.table().number("pageSize")).isEqualTo(10);
        // 引用清单去重后按出现顺序写入 lc_page.ref_fields，删字段时按它反查（§1.5）
        assertThat(design.referencedFields()).containsExactly("orderNo", "status", "amount", "deliveryDate", "remark");
    }

    @Test
    void 查询区与工具栏都可以整块不画() {
        // 只画一张表和一台弹窗：页面能看能改，没有查询条件、没有批量按钮
        PageDesign only = PageDesign.parse(page(TABLE, FORM), context());
        assertThat(only.blocks()).hasSize(2);
        assertThat(only.search()).isEmpty();
        assertThat(only.toolbar()).isEmpty();
        assertThat(only.rowActions()).isEmpty();
        // 只读台账：不摆表单就没有新增与编辑，页面照样成立
        PageDesign readonly = PageDesign.parse(page(SEARCH,
                toolbar(control("Button", "label=导出", "action=export")), TABLE,
                rowActions(control("Button", "label=刷新", "action=refresh"))), context());
        assertThat(readonly.form()).isEmpty();
        assertThat(readonly.referencedFields()).containsExactly("orderNo", "status", "amount", "deliveryDate");
    }

    @Test
    void 协议版本与页面类型不认识就拒绝() {
        assertThatThrownBy(() -> PageDesign.parse("""
                { "version": "2.0", "pageType": "list", "modelRef": "sales_order",
                  "layout": { "type": "PageContainer", "children": [%s] } }
                """.formatted(TABLE), context()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("version").hasMessageContaining("1.0");
        assertThatThrownBy(() -> PageDesign.parse("""
                { "version": "1.0", "pageType": "detail", "modelRef": "sales_order",
                  "layout": { "type": "PageContainer", "children": [%s] } }
                """.formatted(TABLE), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("第一期只支持列表页");
        assertThatThrownBy(() -> PageDesign.parse("""
                { "version": "1.0", "modelRef": "sales_order",
                  "layout": { "type": "PageContainer", "children": [%s] } }
                """.formatted(TABLE), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("pageType");
    }

    @Test
    void 不是JSON对象时说的话人能看懂() {
        // 存草稿最常撞到的三种：一次都没保存过的空串、被截断的半截、顶层写成数组
        assertThatThrownBy(() -> PageDesign.parse("   ", context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不能为空");
        assertThatThrownBy(() -> PageDesign.parse(fullPage().substring(0, 180), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不是合法的 JSON");
        assertThatThrownBy(() -> PageDesign.parse("[]", context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("顶层必须是对象");
    }

    @Test
    void 根必须是PageContainer() {
        assertThatThrownBy(() -> PageDesign.parse(page(TABLE, FORM).replace("PageContainer", "SearchForm"), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("layout 必须是 PageContainer");
        assertThatThrownBy(() -> PageDesign.parse("""
                { "version": "1.0", "pageType": "list", "modelRef": "sales_order" }
                """, context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("layout");
    }

    @Test
    void 页面下只放容器控件不能裸放() {
        // 裸控件生成出来不知道该摆进查询区还是表单区，等于在页面顶上丢一个没标签的格子
        assertThatThrownBy(() -> PageDesign.parse(page(
                control("TextInput", "label=订单编号", "field=orderNo"), TABLE, FORM), context()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PageContainer 下只能放容器")
                .hasMessageContaining("SearchForm");
    }

    @Test
    void 白名单外的组件被拒时列出第一期支持哪些() {
        assertThatThrownBy(() -> PageDesign.parse(page(search(control("Carousel", "field=status")), TABLE, FORM), context()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的组件 Carousel")
                .hasMessageContaining("DateRangePicker");
        // 部门 / 用户 / 上传 / 开关这些控件的取数来源不由模型推导，第一期不产出（方案 §3.3 里排在后面）
        for (String unsupported : List.of("Upload", "ImageField", "DeptSelect", "UserSelect", "TreeSelect", "Switch", "Tag", "Metric")) {
            assertThatThrownBy(() -> PageDesign.parse(page(TABLE, form(control(unsupported, "field=remark"))), context()))
                    .as("%s 第一期不该产出来", unsupported)
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(unsupported);
        }
    }

    @Test
    void 控件必须待在允许它的容器里() {
        // 按钮不是查询条件：摆进查询区，生成出来是一个点了不改任何东西的格子
        assertThatThrownBy(() -> PageDesign.parse(
                page(search(control("Button", "label=查询", "action=refresh")), TABLE, FORM), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("SearchForm 里不能放 Button");
        // 查询控件摆进工具栏，页面上会多出一格没有标签的输入框
        assertThatThrownBy(() -> PageDesign.parse(
                page(toolbar(control("TextInput", "label=订单编号", "field=orderNo")), TABLE, FORM), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Toolbar 里只能放 Button");
        // 表格的列不是组件，只认 columns；写成 children 等于让生成器猜这是列还是控件
        assertThatThrownBy(() -> PageDesign.parse(page(
                "{ \"type\": \"DataTable\", \"children\": [ " + column("orderNo", "订单编号") + " ] }", FORM), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("DataTable 只用 columns");
        // 列表页没有表格就不成其为列表页
        assertThatThrownBy(() -> PageDesign.parse(page(SEARCH, TOOLBAR, FORM), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("必须有 DataTable");
        // 一列都没有的表格只剩「操作」列，等于把整张表的数据藏起来
        assertThatThrownBy(() -> PageDesign.parse(page(table(), FORM), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("至少要声明一列");
        // 列写成裸字符串，生成器拿不到 field，页面上一列空白
        assertThatThrownBy(() -> PageDesign.parse(page(
                "{ \"type\": \"DataTable\", \"columns\": [ \"orderNo\" ] }", FORM), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("列声明必须是一个对象");
    }

    @Test
    void 绑定字段必须是模型真实字段且报错带着可用清单() {
        assertThatThrownBy(() -> PageDesign.parse(
                page(table(column("amounts", "订单金额", "render=money")), FORM), context()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("amounts")
                .hasMessageContaining("不是模型 sales_order 的字段")
                .hasMessageContaining("layout.children")
                .hasMessageContaining("orderNo");
        // 漏填 field 的控件在页面上就是一个没有取数来源的空框
        assertThatThrownBy(() -> PageDesign.parse(page(TABLE, form(control("TextInput", "label=备注"))), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("必须绑定字段");
        // 设计是从别处复制来的：modelRef 与页面归属的模型对不上，字段清单根本不是那一份
        assertThatThrownBy(() -> PageDesign.parse(
                fullPage().replace("\"modelRef\": \"sales_order\"", "\"modelRef\": \"order_item\""), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("modelRef");
    }

    @Test
    void 同一区块里同一字段不能出现两次() {
        // 表单里同字段两遍：生成的 TS 类型重复声明一个成员，交付项目 vue-tsc 当场报错
        assertThatThrownBy(() -> PageDesign.parse(page(TABLE, form(
                control("Textarea", "label=备注", "field=remark"),
                control("TextInput", "label=备注副本", "field=remark"))), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("重复绑定 remark");
        // 查询区、表格、表单各绑一次是正常形态：同字段既能查、又能看、又能改
        assertThatCode(() -> PageDesign.parse(fullPage(), context())).doesNotThrowAnyException();
    }

    @Test
    void 按钮动作只认内置枚举且不接受手写权限点() {
        // 没有名字的按钮没人知道点下去做什么，页面上只剩一个图标
        assertThatThrownBy(() -> PageDesign.parse(page(TABLE, toolbar(control("Button", "action=export")), FORM), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("必须给出 label");
        assertThatThrownBy(() -> PageDesign.parse(page(TABLE, toolbar(control("Button", "label=提交")), FORM), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("必须选一个动作");
        assertThatThrownBy(() -> PageDesign.parse(page(TABLE, toolbar(control("Button", "label=提交", "action=submit")), FORM), context()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未知的动作 submit")
                .hasMessageContaining("create");
        // 自定义动作要在交付项目 overrides/ 里落 handler 桩，而覆写怎么被发现、盖到 B 档哪个区块上还没定稿（§10.2 第 3 项）
        assertThatThrownBy(() -> PageDesign.parse(page(TABLE, toolbar(control("Button", "label=审核", "action=custom:audit")), FORM), context()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("自定义动作").hasMessageContaining("overrides");
        // 详情按钮要跳一个第一期不生成的详情页，与其留个指向没有的路由的链接，不如保存时就说明
        assertThatThrownBy(() -> PageDesign.parse(page(TABLE, rowActions(control("Button", "label=详情", "action=view"))), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("详情页");
        // 权限点由动作推导（create→add、edit→edit…）。手写的那串与生成的菜单种子迟早对不上，
        // 对不上的表现是按钮永远灰，而菜单里权限点看着是齐的
        assertThatThrownBy(() -> PageDesign.parse(
                page(TABLE, toolbar(control("Button", "label=导出", "action=export", "permission=order:export")), FORM), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("权限点由动作推导");
        // 五个内置动作都认：刷新不占权限点，其余按动作推导
        assertThatCode(() -> PageDesign.parse(page(TABLE, toolbar(
                control("Button", "label=新增", "action=create"),
                control("Button", "label=导出", "action=export"),
                control("Button", "label=刷新", "action=refresh")),
                rowActions(control("Button", "label=编辑", "action=edit"),
                        control("Button", "label=删除", "action=delete", "danger=true")), FORM), context()))
                .doesNotThrowAnyException();
    }

    @Test
    void 列的渲染与对齐必须与字段类型相容() {
        // money 按数字串渲染并右对齐，给文本列就是一串空值
        assertThatThrownBy(() -> PageDesign.parse(
                page(table(column("orderNo", "订单编号", "render=money")), FORM), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("render=money");
        // tag 要查字典翻译取值，没挂字典的字段翻出来是一片空白
        assertThatThrownBy(() -> PageDesign.parse(
                page(table(column("remark", "备注", "render=tag")), FORM), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("字典");
        // datetime 只在日期与时间列上有意义
        assertThatThrownBy(() -> PageDesign.parse(
                page(table(column("amount", "订单金额", "render=datetime")), FORM), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("render=datetime");
        assertThatThrownBy(() -> PageDesign.parse(
                page(table(column("orderNo", "订单编号", "render=timestamp")), FORM), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("未知的列渲染 timestamp");
        assertThatThrownBy(() -> PageDesign.parse(
                page(table(column("orderNo", "订单编号", "align=middle")), FORM), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("align");
        assertThatThrownBy(() -> PageDesign.parse(
                page(table(column("orderNo", "订单编号", "width=-1")), FORM), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("width");
    }

    @Test
    void 查询区控件与查询方式必须相容() {
        // 区间在生成物里拆成两个参数（xxxStart / xxxEnd），文本列给不起
        assertThatThrownBy(() -> PageDesign.parse(page(
                search(control("TextInput", "label=订单编号", "field=orderNo", "queryType=BETWEEN")), TABLE, FORM), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("BETWEEN");
        // 前置通配的 LIKE 只对文本列有意义，数字列等于让人打 12 去匹配 12.00，而且用不上索引
        assertThatThrownBy(() -> PageDesign.parse(page(
                search(control("NumberInput", "label=订单金额", "field=amount", "queryType=LIKE")), TABLE, FORM), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("LIKE");
        // 区间控件只能配 BETWEEN：配 = 就是一个框要往两个参数里塞值
        assertThatThrownBy(() -> PageDesign.parse(page(
                search(control("DateRangePicker", "label=交货日期", "field=deliveryDate", "queryType==")), TABLE, FORM), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("DateRangePicker 只能配 BETWEEN");
        assertThatCode(() -> PageDesign.parse(page(
                search(control("DateRangePicker", "label=交货日期", "field=deliveryDate", "queryType=BETWEEN")), TABLE, FORM), context()))
                .doesNotThrowAnyException();
        // 日期字段配日期控件：给个文本框让人手打 2026/11/4 这种后端收不下的写法
        assertThatThrownBy(() -> PageDesign.parse(page(
                search(control("TextInput", "label=交货日期", "field=deliveryDate", "queryType==")), TABLE, FORM), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("DateInput");
        // 区间控件摆进表单：表单只写一个值，没有两端可写
        assertThatThrownBy(() -> PageDesign.parse(page(TABLE, form(
                control("DateRangePicker", "label=交货日期", "field=deliveryDate"))), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("FormContainer 里不能放 DateRangePicker");
        // 控件与字段类型是双向的：日期框绑文本列同样生成不出能用的页面
        assertThatThrownBy(() -> PageDesign.parse(page(
                search(control("DateInput", "label=订单编号", "field=orderNo")), TABLE, FORM), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("只能绑日期字段");
        assertThatThrownBy(() -> PageDesign.parse(page(
                search(control("NumberInput", "label=订单编号", "field=orderNo")), TABLE, FORM), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("只能绑数字字段");
        // 查询条件没有「必填」这一说：留空就是不限制，配上去只会让人以为查不出数据
        assertThatThrownBy(() -> PageDesign.parse(page(
                search(control("TextInput", "label=订单编号", "field=orderNo", "required=true")), TABLE, FORM), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("查询条件没有必填");
    }

    @Test
    void 表单里的字段必须模型允许写入且不收自增主键() {
        // 模型侧 is_insert / is_edit 都关掉的字段没有写入路径，表单收了也是在页面上填完再丢
        assertThatThrownBy(() -> PageDesign.parse(page(TABLE, form(
                control("DateInput", "label=创建时间", "field=createTime"))), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不允许写入");
        // 自增主键进表单是 5G 的真机缺陷：值由数据库给，收进来等于让人猜下一个 id，
        // 还会让页面的表单类型多一个成员、与硬编码的 id 撞成重复声明
        assertThatThrownBy(() -> PageDesign.parse(page(TABLE, form(
                control("NumberInput", "label=主键", "field=id"))), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("自增主键");
    }

    @Test
    void 属性只认这个组件支持的那几个() {
        // 字典挂在字段上（lc_field.dict_type）。设计里再写一遍就是两份事实，翻译取值会打架
        assertThatThrownBy(() -> PageDesign.parse(page(TABLE, form(
                control("DictSelect", "label=状态", "field=status", "dictType=other_status"))), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("字典");
        // 小数位由模型的 decimal 标度给。这里手写一个就是 5G 那个金额静默取整缺陷的另一种犯法
        assertThatThrownBy(() -> PageDesign.parse(page(TABLE, form(
                control("NumberInput", "label=订单金额", "field=amount", "precision=0"))), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("precision");
        assertThatThrownBy(() -> PageDesign.parse(page(
                "{ \"type\": \"DataTable\", \"props\": { \"pageSze\": 10 }, \"columns\": [ " + column("orderNo", "订单编号") + " ] }",
                FORM), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("pageSze");
    }

    @Test
    void 容器最多两层且节点数有上限() {
        // PageContainer > 容器 > 控件就是两层容器。再深一格是画布上点不到、生成物里也没处摆的方块
        String nested = """
                { "type": "Toolbar", "children": [
                  { "type": "SearchForm", "children": [
                    { "type": "TextInput", "props": { "label": "订单编号", "field": "orderNo" } } ] } ] }
                """;
        assertThatThrownBy(() -> PageDesign.parse(page(nested, TABLE, FORM), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("两层");

        List<String> many = new ArrayList<>();
        for (int i = 0; i < 210; i++) {
            many.add(control("TextInput", "label=编号" + i, "field=orderNo"));
        }
        assertThatThrownBy(() -> PageDesign.parse(page(search(many.toArray(String[]::new))), context()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("节点数");
    }

    @Test
    void 画了表单弹窗就必须收齐数据库要求必须有值的列() {
        // 模型里 status 是 NOT NULL 且没有默认值：表单没摆这一格，生成的 INSERT 就没有这一列，
        // 新增点下去是运行期 500 —— 而这种错在页面上一眼看不到，只有数据库知道
        String missing = page(TABLE, form(
                control("TextInput", "label=订单编号", "field=orderNo"),
                control("NumberInput", "label=订单金额", "field=amount")));
        DesignContext strict = context(Map.of("status", 0));
        assertThatThrownBy(() -> PageDesign.parse(missing, strict))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("status")
                .hasMessageContaining("必须出现在表单里");
        // 补上那一格就过了
        assertThatCode(() -> PageDesign.parse(page(TABLE, form(
                control("TextInput", "label=订单编号", "field=orderNo"),
                control("NumberInput", "label=订单金额", "field=amount"),
                control("NumberInput", "label=状态", "field=status"))), strict)).doesNotThrowAnyException();
        // 有默认值的列不要求摆进表单（创建时间这类由数据库自己给）
        assertThatCode(() -> PageDesign.parse(missing, context(Map.of("status", 0, "amount", 0, "orderNo", 0),
                Map.of("status", "0")))).doesNotThrowAnyException();
        // 一个字段都不摆 = 只读台账，这条规则不适用：没有新增按钮就没有 INSERT
        assertThatCode(() -> PageDesign.parse(page(SEARCH, TABLE), context(Map.of("status", 0))))
                .doesNotThrowAnyException();
    }

    @Test
    void 有新增或编辑按钮就必须画表单弹窗() {
        assertThatThrownBy(() -> PageDesign.parse(page(SEARCH, TOOLBAR, TABLE), context()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("create")
                .hasMessageContaining("FormContainer");
        // 只读页面（没有写动作）可以只有表格
        assertThatCode(() -> PageDesign.parse(page(SEARCH, TABLE, rowActions(
                control("Button", "label=刷新", "action=refresh"))), context())).doesNotThrowAnyException();
    }

    /* ── 夹具：小构造器拼 JSON，用例只写自己那一条差异 ── */

    private static String page(String... blocks) {
        return """
                { "version": "1.0", "pageType": "list", "modelRef": "sales_order",
                  "layout": { "type": "PageContainer", "props": { "title": "销售订单", "desc": "管理所有销售订单" },
                              "children": [%s] } }
                """.formatted(String.join(",\n", blocks));
    }

    private static String search(String... controls) {
        return block("SearchForm", "\"columns\": 3", List.of(controls));
    }

    private static String toolbar(String... buttons) {
        return block("Toolbar", null, List.of(buttons));
    }

    private static String rowActions(String... buttons) {
        return block("RowActions", "\"priority\": 2", List.of(buttons));
    }

    private static String form(String... controls) {
        return block("FormContainer", null, List.of(controls));
    }

    private static String table(String... columns) {
        return "{ \"type\": \"DataTable\", \"props\": { \"pagination\": true, \"pageSize\": 10 }, \"columns\": [\n"
                + String.join(",\n", columns) + "\n] }";
    }

    private static String block(String type, String props, List<String> children) {
        return "{ \"type\": \"" + type + "\"" + (props == null ? "" : ", \"props\": { " + props + " }")
                + ", \"children\": [\n" + String.join(",\n", children) + "\n] }";
    }

    /** {@code key=value} 形式的属性：不带引号的原样写（数字与布尔），带引号的由 {@link #props} 补 */
    private static String control(String type, String... props) {
        return "{ \"type\": \"" + type + "\", \"props\": { " + String.join(", ", props(props)) + " } }";
    }

    private static String column(String field, String title, String... props) {
        StringBuilder json = new StringBuilder("{ \"field\": \"").append(field)
                .append("\", \"title\": \"").append(title).append("\"");
        for (String prop : props(props)) {
            json.append(", ").append(prop);
        }
        return json.append(" }").toString();
    }

    /** {@code label=订单编号} → {@code "label": "订单编号"}；已经是 {@code "k": v} 的原样留着（用例里手写引号时用） */
    private static List<String> props(String... props) {
        List<String> out = new ArrayList<>();
        for (String prop : props) {
            int split = prop.indexOf('=');
            if (split < 0 || prop.startsWith("\"")) {
                out.add(prop);
                continue;
            }
            String value = prop.substring(split + 1);
            if (value.isBlank()) {
                out.add("\"" + prop.substring(0, split) + "\": \" \"");
                continue;
            }
            boolean scalar = value.matches("-?\\d+") || value.matches("true|false");
            out.add("\"" + prop.substring(0, split) + "\": " + (scalar ? value : "\"" + value + "\""));
        }
        return out;
    }

    /** 模型侧的事实（字段编码、类型、字典与可写性）。设计校验只认它，不认设计自己的声明 */
    private static DesignContext context() {
        return context(Map.of());
    }

    /** 只覆盖与断言有关的那几列的可空性，其余按库里的默认（可空）*/
    private static DesignContext context(Map<String, Integer> nullableOverrides) {
        return context(nullableOverrides, Map.of());
    }

    private static DesignContext context(Map<String, Integer> nullableOverrides, Map<String, String> defaults) {
        List<Map<String, Object>> rows = List.of(
                field("id", "主键", "Long", null, true, false, false, nullableOverrides, defaults),
                field("orderNo", "订单编号", "String", null, false, true, true, nullableOverrides, defaults),
                field("amount", "订单金额", "BigDecimal", null, false, true, true, nullableOverrides, defaults),
                field("status", "状态", "Integer", "order_status", false, true, true, nullableOverrides, defaults),
                field("deliveryDate", "交货日期", "LocalDate", null, false, true, true, nullableOverrides, defaults),
                field("remark", "备注", "String", null, false, true, true, nullableOverrides, defaults),
                field("createTime", "创建时间", "LocalDateTime", null, false, false, false, nullableOverrides, defaults));
        return DesignContext.of("sales_order", rows);
    }

    /** lc_field 行的形状：Mapper 查出来就是这个样子，测试照着造 */
    private static Map<String, Object> field(String code, String name, String javaType, String dictType,
                                             boolean primary, boolean insert, boolean edit,
                                             Map<String, Integer> nullableOverrides, Map<String, String> defaults) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("fieldCode", code);
        row.put("fieldName", name);
        row.put("javaType", javaType);
        row.put("dictType", dictType);
        row.put("isPrimary", primary ? 1 : 0);
        row.put("isAutoIncrement", primary ? 1 : 0);
        row.put("isInsert", insert ? 1 : 0);
        row.put("isEdit", edit ? 1 : 0);
        row.put("isNullable", nullableOverrides.getOrDefault(code, 1));
        row.put("defaultValue", defaults.get(code));
        return row;
    }
}

package com.scaffold.lowcode.generate;

import com.scaffold.lowcode.design.DesignContext;
import com.scaffold.lowcode.design.PageDesign;
import com.scaffold.lowcode.design.PageTarget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.scaffold.lowcode.generate.TemplateFixture.model;
import static com.scaffold.lowcode.generate.TemplateFixture.richFields;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 生成期合流：同一套模板同时吃 design_json 与 lc_field（方案 §1.5 ②、PR 6B）。
 *
 * <p>准绳是「页面是形、模型是料」。设计决定哪些格子出现、按什么顺序、叫什么、怎么渲染、点哪个按钮；
 * 类型、约束、DDL、权限点前缀仍由模型给。所以这里的断言都在钉两件事：
 * <b>形跟着设计走</b>，以及<b>料不跟着设计走</b>（改了设计不能把实体与建表语句改出模型之外）。
 *
 * <p>没有设计的那条路（zip 下载与老代码生成页）必须与 PR 5 的形状逐字节一致，
 * 最后一条用例钉的就是这个 —— 合流不能把既有链路一起改掉。
 */
class DesignGenerationTest {

    private final TemplateRenderer renderer = new TemplateRenderer();

    /** 订单模型的三份设计：查询只摆状态、列换成金额在前的两列、表单只收订单号与状态 */
    private static final String DESIGN = """
            { "version": "1.0", "pageType": "list", "modelRef": "order",
              "layout": { "type": "PageContainer", "props": { "title": "订单台账", "desc": "只看得到已支付的那几单" },
                          "children": [
                { "type": "SearchForm", "children": [
                  { "type": "DictSelect", "props": { "label": "订单状态", "field": "status", "queryType": "=" } }
                ] },
                { "type": "Toolbar", "children": [
                  { "type": "Button", "props": { "label": "录入订单", "action": "create", "variant": "primary" } }
                ] },
                { "type": "DataTable", "props": { "pageSize": 20 }, "columns": [
                  { "field": "amount", "title": "金额", "align": "right", "render": "money" },
                  { "field": "status", "title": "状态", "width": 90, "render": "tag" },
                  { "field": "remark", "title": "备注", "minWidth": 200 }
                ] },
                { "type": "RowActions", "children": [
                  { "type": "Button", "props": { "label": "改", "action": "edit" } }
                ] },
                { "type": "FormContainer", "children": [
                  { "type": "TextInput", "props": { "label": "订单号", "field": "orderNo", "placeholder": "打完整编号" } },
                  { "type": "DictSelect", "props": { "label": "订单状态", "field": "status" } },
                  { "type": "Textarea", "props": { "label": "备注", "field": "remark" } }
                ] }
              ] } }
            """;

    private PageTarget target() {
        DesignContext context = DesignContext.of("order", richFields());
        return new PageTarget("订单列表", "/trade/orders", "cart", 3, PageDesign.parse(DESIGN, context));
    }

    @Test
    void 查询条件跟着设计走而不再看模型的勾选() {
        // 模型里 orderNo / amount / payTime 都勾了 is_query，设计一个都没摆；status 摆在了查询区
        Map<String, Object> root = GenerationInput.of(model(true, true), richFields(), target());

        assertThat(root.get("queryFields")).isInstanceOfSatisfying(List.class, fields -> {
            assertThat(fields).extracting(item -> ((Map<?, ?>) item).get("code")).containsExactly("status");
        });
        assertThat(codes(root.get("queryParams"))).containsExactly("status");

        String mapper = renderer.render("backend/Mapper.java.ftl", root);
        assertThat(mapper).contains("status").doesNotContain("orderNoStart").doesNotContain("LIKE");
        String controller = renderer.render("backend/Controller.java.ftl", root);
        assertThat(controller).doesNotContain("String orderNo").doesNotContain("LocalDateTime payTimeStart");

        // 料不跟着设计走：实体与建表语句仍是模型的全部字段
        String entity = renderer.render("backend/Entity.java.ftl", root);
        assertThat(entity).contains("private String orderNo;").contains("private LocalDateTime payTime;");
        assertThat(renderer.render("sql/Table.sql.ftl", root)).contains("pay_time").contains("order_no");
    }

    @Test
    void 列的顺序标题与渲染都来自设计() {
        Map<String, Object> root = GenerationInput.of(model(true, true), richFields(), target());
        String page = renderer.render("frontend/ListView.vue.ftl", root).replace("\r", "");

        // 顺序照设计：金额、状态、备注；主键不进列但仍在行数据里（编辑与删除要靠它）
        int amount = page.indexOf("title: '金额'");
        int status = page.indexOf("title: '状态'");
        int remark = page.indexOf("title: '备注'");
        assertThat(amount).isPositive().isLessThan(status).isLessThan(remark);
        assertThat(page).contains("{ key: 'amount', title: '金额', minWidth: 110, align: 'right' },")
                .contains("{ key: 'status', title: '状态', width: 90 },")
                .contains("{ key: 'remark', title: '备注', minWidth: 200 },");
        // render 决定插槽：tag 出字典标签，money 出等宽数字
        assertThat(page).contains("<template #cell-status=\"{ row }\"><DictTag type=\"sys_order_status\"")
                .contains("<template #cell-amount=\"{ row }\"><span class=\"tnum\">");
        // 没被设计摆进列的字段不再出现在表头
        assertThat(page).doesNotContain("title: '订单号'").doesNotContain("title: '支付时间'");

        // Row 仍带主键：页面不显示 id，但 openEdit 与 remove 都读它
        assertThat(renderer.render("backend/Row.java.ftl", root)).contains("private Long id;");
    }

    @Test
    void 表单只提交设计摆出来的格子() {
        Map<String, Object> root = GenerationInput.of(model(true, true), richFields(), target());

        assertThat(codes(root.get("formFields"))).containsExactly("orderNo", "status", "remark");
        // 模型的 is_insert / is_edit 仍然说话：payTime 可写但设计没摆 → 不进写入语句；
        // tags 既不可写也没摆，两处都不该出现
        assertThat(codes(root.get("insertFields"))).containsExactly("orderNo", "status", "remark");
        assertThat(codes(root.get("updateFields"))).containsExactly("orderNo", "status", "remark");
        String write = renderer.render("backend/WriteRequest.java.ftl", root);
        assertThat(write).contains("private String orderNo;").contains("private String remark;")
                .doesNotContain("payTime").doesNotContain("tags");
        // 写入语句的列就是设计表单那三格：payTime 模型里可写但设计没摆，不该被写；
        // detail 查询仍取整行（编辑前要回填没进列表的列），那一行有 pay_time 是对的
        assertThat(renderer.render("backend/Mapper.java.ftl", root))
                .contains("INSERT INTO biz_order (order_no, status, remark, create_by, update_by)");

        String page = renderer.render("frontend/ListView.vue.ftl", root).replace("\r", "");
        // 设计里的中文名与占位提示进了页面
        assertThat(page).contains("label=\"订单号\"").contains("placeholder=\"打完整编号\"");
        // status 在模型里必填 → 前端星号与后端注解都要在
        assertThat(page).contains("status: [required('请选择订单状态')]");
    }

    @Test
    void 按钮与工具栏由设计的动作决定() {
        Map<String, Object> root = GenerationInput.of(model(true, true), richFields(), target());
        String page = renderer.render("frontend/ListView.vue.ftl", root).replace("\r", "");

        assertThat(page).contains("<PermissionButton perm=\"order:order:add\" variant=\"primary\" icon=\"plus\" @click=\"openCreate\">录入订单</PermissionButton>");
        assertThat(page).contains("icon=\"edit\" label=\"改\" @click=\"openEdit(row)\" />");
        // 设计里没有删除按钮 → 行操作不生成删除，也不给 :remove 权限点
        assertThat(page).doesNotContain("order:order:remove");
        // 设计里没有导出按钮 → 不接 exporter，ListPage 表头的导出按钮随之消失
        assertThat(page).doesNotContain("exporter:").doesNotContain("exportName:");

        // 菜单：名字、图标、排序、路由都跟着页面走，权限点仍由模型派生
        String menu = renderer.render("sql/Menu.sql.ftl", root);
        assertThat(menu).contains("'订单列表'").contains("'cart'").contains("/trade/orders");
        assertThat(menu).doesNotContain("order:order:remove");
    }

    @Test
    void 没有设计时形状与PR5一致() {
        Map<String, Object> root = GenerationInput.of(model(true, true), richFields());
        String page = renderer.render("frontend/ListView.vue.ftl", root).replace("\r", "");

        // 列表列回到模型的 is_list，查询回到 is_query，标题回到字段名
        assertThat(page).contains("{ key: 'orderNo', title: '订单号', minWidth: 160 },")
                .contains("{ key: 'status', title: '状态', minWidth: 110, align: 'right' },");
        assertThat(codes(root.get("queryParams"))).containsExactly("orderNo", "amountStart", "amountEnd",
                "payTimeStart", "payTimeEnd", "status", "tags");
        assertThat(page).contains("exporter: params => orderApi.export(params)").contains("exportName: '订单主表.csv'");
        assertThat(page).contains("title=\"订单主表管理\"").contains("新增订单");
        assertThat(renderer.render("sql/Menu.sql.ftl", root)).contains("'订单主表'", "'grid'");
    }

    /**
     * 菜单权限点要收设计里出现过的全部动作，不能只看在工具栏那几个。
     *
     * <p>这条是 PR 7 真机演练撞出来的：设计把「改」「删」摆在行操作里，页面上两颗按钮都在，
     * 菜单却只种了 :add —— 项目里的角色拿不到 :edit / :remove，按钮点了就是 403。
     * 「看着配置对了、实际不能用」比报错难查得多，所以这里既钉工具栏那半边，也钉行操作那半边。
     */
    @Test
    void 行操作里的动作也要种进菜单权限点() {
        Map<String, Object> root = GenerationInput.of(model(true, true), richFields(), target());
        String menu = renderer.render("sql/Menu.sql.ftl", root);
        assertThat(menu).contains("order:order:add").contains("order:order:edit").doesNotContain("order:order:remove");

        String withDelete = DESIGN.replace("{ \"type\": \"Button\", \"props\": { \"label\": \"改\", \"action\": \"edit\" } }",
                "{ \"type\": \"Button\", \"props\": { \"label\": \"改\", \"action\": \"edit\" } }, "
                        + "{ \"type\": \"Button\", \"props\": { \"label\": \"删\", \"action\": \"delete\", \"danger\": true } }");
        assertThat(withDelete).as("夹具里那行改按钮的原文要先存在，否则这条替换是空跑").isNotEqualTo(DESIGN);
        Map<String, Object> both = GenerationInput.of(model(true, true), richFields(),
                new PageTarget("订单列表", null, null, null, PageDesign.parse(withDelete, DesignContext.of("order", richFields()))));
        String nextMenu = renderer.render("sql/Menu.sql.ftl", both);
        assertThat(nextMenu).contains("order:order:edit").contains("order:order:remove");
        // 排序按「新增 / 编辑 / 删除」固定，不随设计里的摆放顺序漂
        assertThat(nextMenu.indexOf("order:order:edit")).isLessThan(nextMenu.indexOf("order:order:remove"));
    }

    /** 设计没填画布标题时，页面标题兜到页面名而不是模型编码（画布上也是这么兜的，两处一个口径） */
    @Test
    void 设计没填标题时页面标题兜到页面名而不是模型编码() {
        String withoutProps = DESIGN.replace(", \"props\": { \"title\": \"订单台账\", \"desc\": \"只看得到已支付的那几单\" }", "");
        assertThat(withoutProps).as("夹具里 PageContainer 那份属性要先存在").isNotEqualTo(DESIGN);
        PageTarget target = new PageTarget("销售订单页", null, null, null,
                PageDesign.parse(withoutProps, DesignContext.of("order", richFields())));

        Map<String, Object> root = GenerationInput.of(model(true, true), richFields(), target);
        assertThat(root.get("pageTitle")).isEqualTo("销售订单页");
        // 菜单名本来就用页面名，两处不该给出两个答案
        assertThat(root.get("menuName")).isEqualTo("销售订单页");
        assertThat(renderer.render("frontend/ListView.vue.ftl", root)).contains("title=\"销售订单页\"");
        // 画布上真填了标题就以它为准
        assertThat(GenerationInput.of(model(true, true), richFields(), target()).get("pageTitle"))
                .isEqualTo("订单台账");
    }

    /** 字段清单读 code，查询参数清单读 name（同一个键在两张表里叫法不同，断言里不想再分两个方法） */
    private static List<String> codes(Object list) {
        return ((List<?>) list).stream().map(item -> {
            Map<?, ?> map = (Map<?, ?>) item;
            return String.valueOf(map.get("code") != null ? map.get("code") : map.get("name"));
        }).toList();
    }
}

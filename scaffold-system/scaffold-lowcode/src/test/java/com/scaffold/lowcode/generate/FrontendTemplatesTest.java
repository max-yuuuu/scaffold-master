package com.scaffold.lowcode.generate;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.scaffold.lowcode.generate.TemplateFixture.model;
import static com.scaffold.lowcode.generate.TemplateFixture.project;
import static com.scaffold.lowcode.generate.TemplateFixture.quotedRemarkFields;
import static com.scaffold.lowcode.generate.TemplateFixture.richFields;
import static com.scaffold.lowcode.generate.TemplateFixture.simpleFields;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 前端两份模板的回归测试（PR 5D）。
 *
 * <p>产物要在交付项目里过 {@code vue-tsc}，光靠肉眼读 .ftl 保证不了：这里盯的是「引用走哪条路径」
 * 与「类型形状对不对」两件事。真机 typecheck 的演练方式见 changelog —— 把渲染出的两份文件放进
 * 宿主的 {@code scaffold-vue/src} 跑一次 typecheck 再删掉，本模块测试不引 node 工具链。
 */
class FrontendTemplatesTest {

    private final TemplateRenderer renderer = new TemplateRenderer();

    @Test
    void 接口文件走宿主http客户端类型从物料包取() {
        String api = api(true, true);
        assertThat(api).contains("import { httpClient } from '@/api/http'");
        assertThat(api).contains("import type { PageResult, QueryParams } from '@yuanda-lowcode-ai/scaffold-ui/types'");
        assertThat(api).contains("export interface OrderRow {").contains("export interface OrderWriteRequest {");
        // 路径就是后端 Controller 的 @RequestMapping，逐个方法对齐
        assertThat(api).contains("httpClient.get<PageResult<OrderRow>>('/order/order', query)")
                .contains("httpClient.post<number>('/order/order', body)")
                .contains("httpClient.put<void>(`/order/order/${id}`, body)")
                .contains("httpClient.delete<void>(`/order/order/${id}`)")
                .contains("httpClient.download('/order/order/export', query)");
        // 编辑前要取整行：列表行只有勾选进列表的字段
        assertThat(api).contains("detail: (id: number) => httpClient.get<OrderWriteRequest & { id: number }>");
        // 不引物料的注册表、也不碰宿主 api 的汇总出口
        assertThat(api).doesNotContain("from '@/api'").doesNotContain("@/components");
    }

    @Test
    void 列表行的可空性跟库里列的约束一致() {
        String api = api(true, true);
        assertThat(api).contains("  id: number")
                .contains("  orderNo: string")
                // 可空文本列 → string | null；数字列在入参里一律可空（Java 侧是 Integer）
                .contains("  payTime: string | null")
                .contains("  amount: number | null");
        // 审计四列在行上是可选的：老数据可能没有
        assertThat(api).contains("  createTime?: string | null");
        assertThat(api(false, false)).doesNotContain("  createBy?: string | null");
    }

    @Test
    void 页面只从物料包子路径引组件不写app别名() {
        String page = view(true, true);
        assertThat(page).contains("from '@yuanda-lowcode-ai/scaffold-ui/components/ui'")
                .contains("from '@yuanda-lowcode-ai/scaffold-ui/components/business'")
                .contains("from '@yuanda-lowcode-ai/scaffold-ui/composables/list'")
                .contains("from '@yuanda-lowcode-ai/scaffold-ui/utils/validate'");
        assertThat(page).doesNotContain("@/components").doesNotContain("@/composables");
        // 业务接口是宿主自己的文件，按模块路径直引，不经过 api 汇总出口（那是 A 档，平台不改）
        assertThat(page).contains("import { orderApi, type OrderRow, type OrderWriteRequest } from '@/api/order/order'");
        // 只列这个页面真会用到的组件
        assertThat(page).contains("import { DateInput, Field, NumberInput, TextInput, Textarea, type Column } from");
        assertThat(page).doesNotContain("DeptTree").doesNotContain("Upload");
    }

    @Test
    void 版式固定走ListPage加useListPage() {
        String page = view(true, true);
        assertThat(page).contains("const list = useListPage<OrderRow, OrderQuery>({")
                .contains("fetch: params => orderApi.list(params),")
                .contains("exporter: params => orderApi.export(params),")
                .contains("exportName: '订单主表.csv',");
        assertThat(page).contains("<ListPage")
                .contains("title=\"订单主表管理\"")
                .contains(":list=\"list\"")
                .contains(":columns=\"columns\"")
                .contains("<template #actions>")
                .contains("<template #search>")
                .contains("<template #cell-actions=\"{ row }\">");
        // 行操作与新增按钮都挂权限点，与后端 @RequiresPermission 同一个串
        assertThat(page).contains("perm=\"order:order:add\"").contains("perm=\"order:order:edit\"")
                .contains("perm=\"order:order:remove\"");
    }

    @Test
    void 字典列出下拉并在单元格里换成标签() {
        String page = view(true, true);
        assertThat(page).contains("<DictSelect v-model=\"list.query.status\" type=\"sys_order_status\" all-label=\"全部\" />");
        assertThat(page).contains("<template #cell-status=\"{ row }\"><DictTag type=\"sys_order_status\" :value=\"row.status\" /></template>");
        assertThat(page).contains("DictSelect").contains("DictTag");
    }

    @Test
    void 查询区按参数类型给控件区间是两个输入框() {
        String page = view(true, true);
        // LIKE 的订单号：文本框 + 回车查询
        assertThat(page).contains("<TextInput v-model=\"list.query.orderNo\" clearable @enter=\"list.search()\" />");
        // 区间的起止是两个独立控件，初值空串 / null，params() 会丢掉空值
        assertThat(page).contains("<NumberInput v-model=\"list.query.amountStart\" />");
        assertThat(page).contains("<DateInput v-model=\"list.query.payTimeStart\" type=\"datetime\" />");
        // 多选（后端收逗号串）如实给一个文本框，不假装是多选控件
        assertThat(page).contains("<TextInput v-model=\"list.query.tags\" clearable @enter=\"list.search()\" />");
        assertThat(page).contains("  amountStart: null,").contains("  orderNo: '',");
    }

    @Test
    void 表单按字段勾的控件渲染必填走validate规则() {
        String page = view(true, true);
        assertThat(page).contains("<Field label=\"订单号\" required wide :error=\"errors.orderNo\">");
        assertThat(page).contains("orderNo: [required('请输入订单号')],");
        assertThat(page).contains("<Textarea v-model=\"form.remark\" :rows=\"3\" />");
        // 提交前 trim 文本，可空的那类用 ?.trim() ?? null（空值传 null 而不是空串）
        assertThat(page).contains("orderNo: form.orderNo.trim(),")
                .contains("remark: form.remark?.trim() ?? null,");
        assertThat(page).contains("if (Object.keys(errors.value).length) return");
    }

    @Test
    void 编辑前先取整行避免清空没显示的字段() {
        String page = view(true, true);
        assertThat(page).contains("const detail = await orderApi.detail(row.id)");
        assertThat(page).contains("Object.assign(form, emptyForm(), detail)");
        // 新增走的是同一份表单，先用空值复位
        assertThat(page).contains("Object.assign(form, emptyForm())");
    }

    @Test
    void 没有查询条件时不出查询区() {
        String page = view(false, false).replace("\r", "");
        assertThat(page).doesNotContain("<template #search>");
        assertThat(page).contains("const list = useListPage<OrderRow, OrderQuery>({")
                .contains("fetch: params => orderApi.list(params),");
        assertThat(page).doesNotContain("createTime");
    }

    @Test
    void 说明里的引号在TS字面量里被转义() {
        Map<String, Object> model = GenerationInput.of(project(), model(true, true), quotedRemarkFields());
        String api = renderer.render("frontend/Api.ts.ftl", model);
        String page = renderer.render("frontend/ListView.vue.ftl", model);
        assertThat(api).doesNotContain("COMMENT");
        assertThat(page).contains("title: '状态\\'正常\\'的说明'");
    }

    @Test
    void 两份前端产物各自两次渲染逐字节一致() {
        Map<String, Object> model = GenerationInput.of(project(), model(true, true), richFields());
        for (String template : List.of("frontend/Api.ts.ftl", "frontend/ListView.vue.ftl")) {
            assertThat(renderer.render(template, model)).as(template).isEqualTo(renderer.render(template, model));
        }
    }

    private String api(boolean audit, boolean logicDelete) {
        return renderer.render("frontend/Api.ts.ftl",
                GenerationInput.of(project(), model(audit, logicDelete), richFields()));
    }

    private String view(boolean audit, boolean logicDelete) {
        String rendered = renderer.render("frontend/ListView.vue.ftl",
                GenerationInput.of(project(), model(audit, logicDelete), audit ? richFields() : simpleFields()));
        return rendered.replace("\r", "");
    }
}

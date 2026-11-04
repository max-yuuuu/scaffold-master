package com.scaffold.lowcode.generate;

import com.scaffold.common.exception.BusinessException;
import com.scaffold.lowcode.generate.TemplateFixture.Field;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.scaffold.lowcode.generate.TemplateFixture.model;
import static com.scaffold.lowcode.generate.TemplateFixture.richFields;
import static com.scaffold.lowcode.generate.TemplateFixture.simpleFields;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 后端 CRUD 五件套模板的回归测试（PR 5B）。
 *
 * <p>断言盯两件事：一是<b>产物能不能编译</b>（包名、import、方法签名、SQL 片段里的列名与参数名对得上），
 * 这些靠肉眼读 .ftl 是看不出来的；二是<b>口径与底座一致</b> —— 查询条件写成「参数为空就是不限制」的
 * 静态 SQL、逐个参数平铺、软删/物删两条路、审计列交给数据库默认值。
 *
 * <p>产物真正进交付项目 {@code mvn compile} 的演练不在这个类里（它需要交付侧的依赖与 pom），
 * 见 changelog 的 PR 5B 验证记录；这里保证的是形状不漂。
 */
class CrudTemplatesTest {

    private final TemplateRenderer renderer = new TemplateRenderer();

    @Test
    void 五个模板都按模型编码落到business子包() {
        Map<String, Object> model = GenerationInput.of(model(true, true), richFields());
        for (String template : List.of("backend/Entity.java.ftl", "backend/Row.java.ftl",
                "backend/WriteRequest.java.ftl", "backend/Mapper.java.ftl", "backend/Service.java.ftl",
                "backend/Controller.java.ftl")) {
            String code = renderer.render(template, model);
            assertThat(code).as(template).startsWith("package com.scaffold.business.order.");
            // B 档提示语必须出现在每个文件头上，读代码的人第一眼就知道这文件会被覆盖
            assertThat(code).as(template).contains("每次发布整文件覆盖");
        }
        assertThat(renderer.render("backend/Entity.java.ftl", model)).contains("public class Order {");
        assertThat(renderer.render("backend/Row.java.ftl", model)).contains("public class OrderRow {");
        assertThat(renderer.render("backend/Mapper.java.ftl", model)).contains("@Mapper\npublic interface OrderMapper {");
        assertThat(renderer.render("backend/Service.java.ftl", model)).contains("public class OrderService {");
        assertThat(renderer.render("backend/Controller.java.ftl", model))
                .contains("@RequestMapping(\"/api/order/order\")")
                .contains("public class OrderController {");
    }

    @Test
    void 列表行只装勾选进列表的字段而入参只装可写的字段() {
        Map<String, Object> model = GenerationInput.of(model(true, true), richFields());
        String row = renderer.render("backend/Row.java.ftl", model);
        assertThat(row).contains("private String orderNo;").contains("private LocalDateTime payTime;")
                .contains("private Long id;");
        // remark 没勾进列表，不该出现在行里；标签同理
        assertThat(row).doesNotContain("private String remark;").doesNotContain("private String tags;");
        // 日期列按前端规范串出参
        assertThat(row).contains("@JsonFormat(pattern = \"yyyy-MM-dd HH:mm:ss\")\n    private LocalDateTime payTime;");

        String write = renderer.render("backend/WriteRequest.java.ftl", model);
        assertThat(write).contains("private String remark;").contains("private String orderNo;");
        // 主键与自增列不进写入参：改它等于让前端能改一个改不动的值
        assertThat(write).doesNotContain("private Long id;");
        assertThat(write).doesNotContain("createBy");
    }

    @Test
    void 查询条件按参数为空就是不限制的静态SQL生成() {
        String mapper = renderer.render("backend/Mapper.java.ftl",
                GenerationInput.of(model(true, true), richFields()));

        // 别名只给列名与驼峰不同的列，id 就是 t.id
        assertThat(mapper).contains("SELECT t.id,\n                   t.order_no AS orderNo,");
        assertThat(mapper).contains("ORDER BY t.id DESC").contains("LIMIT #{offset}, #{limit}");
        // 四种查询方式各自的写法，参数名与 #{ } 一一对应
        // 参数为空的判断也必须写 #{x}：写成裸的 orderNo 会被 MyBatis 当列名，运行期才炸
        assertThat(mapper).contains("AND (#{orderNo} IS NULL OR #{orderNo} = ''\n"
                + "                   OR t.order_no LIKE CONCAT('%', #{orderNo}, '%'))");
        assertThat(mapper).contains("AND (#{amountStart} IS NULL OR t.amount >= #{amountStart})")
                .contains("AND (#{amountEnd} IS NULL OR t.amount <= #{amountEnd})");
        assertThat(mapper).contains("(#{status} IS NULL OR t.status = #{status})");
        assertThat(mapper).contains("AND (#{tags} IS NULL OR #{tags} = ''\n"
                + "                   OR FIND_IN_SET(t.tags, #{tags}))");
        // count 与分页取数共用同一段 WHERE：两处口径不会分叉
        assertThat(mapper.indexOf("WHERE t.deleted = 0")).isNotEqualTo(mapper.lastIndexOf("WHERE t.deleted = 0"));
        assertThat(mapper).contains("long countByQuery(").contains("@Param(\"orderNo\") String orderNo,");
    }

    @Test
    void 唯一列生成查重并由Service在写入前调用() {
        Map<String, Object> model = GenerationInput.of(model(true, true), richFields());
        String mapper = renderer.render("backend/Mapper.java.ftl", model);
        assertThat(mapper).contains("long countByOrderNo(@Param(\"value\") String value, @Param(\"excludeId\") Long excludeId);")
                .contains("AND (#{excludeId} IS NULL OR id <> #{excludeId})");

        String service = renderer.render("backend/Service.java.ftl", model);
        assertThat(service).contains("mapper.countByOrderNo(request.getOrderNo(), null) > 0")
                .contains("mapper.countByOrderNo(request.getOrderNo(), id) > 0")
                .contains("throw new BusinessException(\"订单号已存在\");");
        // 必填：字符串还要判空白，数字只判 null
        assertThat(service).contains("if (request.getOrderNo() == null || request.getOrderNo().isBlank()) {")
                .contains("if (request.getStatus() == null) {");
    }

    @Test
    void 写库语句只碰勾选过的字段并把时间交给数据库() {
        String mapper = renderer.render("backend/Mapper.java.ftl",
                GenerationInput.of(model(true, true), richFields()));
        // 自增主键不进 INSERT，否则会把 AUTO_INCREMENT 顶掉
        assertThat(mapper).contains("INSERT INTO biz_order (order_no, amount, pay_time, status, remark, create_by, update_by)");
        assertThat(mapper).contains("VALUES (#{order.orderNo}, #{order.amount}, #{order.payTime}, #{order.status}, #{order.remark}, #{operator}, #{operator})");
        assertThat(mapper).contains("@Options(useGeneratedKeys = true, keyProperty = \"id\")");
        // UPDATE 用整行覆盖，没勾选可编辑的字段一个都不碰；时间交给 ON UPDATE CURRENT_TIMESTAMP
        assertThat(mapper).contains("SET order_no = #{order.orderNo},");
        assertThat(mapper).contains("update_by = #{operator}\n            WHERE id = #{id} AND deleted = 0");
        assertThat(mapper).doesNotContain("update_time = NOW()");
    }

    @Test
    void 逻辑删除开关决定是翻标记还是真删() {
        Map<String, Object> soft = GenerationInput.of(model(true, true), richFields());
        String softMapper = renderer.render("backend/Mapper.java.ftl", soft);
        assertThat(softMapper).contains("SET deleted = 1, update_by = #{operator}")
                .contains("WHERE id = #{id} AND deleted = 0")
                .doesNotContain("@Delete");
        assertThat(renderer.render("backend/Service.java.ftl", soft))
                .contains("mapper.deleteById(id, SecurityUtils.getUsername())")
                .contains("这张表勾了「逻辑删除」");

        Map<String, Object> hard = GenerationInput.of(model(false, false), simpleFields());
        String hardMapper = renderer.render("backend/Mapper.java.ftl", hard);
        assertThat(hardMapper).contains("import org.apache.ibatis.annotations.Delete;")
                .contains("DELETE FROM biz_order\n            WHERE id = #{id}")
                .doesNotContain("deleted");
        assertThat(renderer.render("backend/Service.java.ftl", hard))
                .contains("mapper.deleteById(id)")
                .contains("历史不可回溯");
    }

    @Test
    void 没有审计列时插入语句不带创建人() {
        String mapper = renderer.render("backend/Mapper.java.ftl",
                GenerationInput.of(model(false, false), simpleFields()));
        assertThat(mapper).contains("INSERT INTO biz_order (name)\n            VALUES (#{order.name})");
        assertThat(mapper).doesNotContain("create_by");
    }

    @Test
    void 没有查询条件时方法签名收成一行() {
        String mapper = renderer.render("backend/Mapper.java.ftl",
                GenerationInput.of(model(false, false), simpleFields()));
        assertThat(mapper).contains("long countByQuery();");
        assertThat(mapper).doesNotContain("countByQuery(\n");

        String service = renderer.render("backend/Service.java.ftl",
                GenerationInput.of(model(false, false), simpleFields()));
        assertThat(service).contains("public String export() {")
                .contains("mapper.countByQuery()").contains("mapper.selectRows(query.offset()");
        String controller = renderer.render("backend/Controller.java.ftl",
                GenerationInput.of(model(false, false), simpleFields()));
        assertThat(controller).contains("service.page(query)").contains("public ResponseEntity<String> export() {")
                .doesNotContain("import org.springframework.format.annotation.DateTimeFormat;");
    }

    @Test
    void 日期查询参数带DateTimeFormat按规范串绑定() {
        String controller = renderer.render("backend/Controller.java.ftl",
                GenerationInput.of(model(true, true), richFields()));
        assertThat(controller).contains("import org.springframework.format.annotation.DateTimeFormat;");
        assertThat(controller).contains("@RequestParam(required = false) "
                + "@DateTimeFormat(pattern = \"yyyy-MM-dd HH:mm:ss\") LocalDateTime payTimeStart,");
        assertThat(controller).contains("@RequestParam(required = false) String orderNo,");
        // 五个入口与权限点
        assertThat(controller).contains("@RequiresPermission(\"order:order:list\")")
                .contains("@RequiresPermission(\"order:order:add\")")
                .contains("@PutMapping(\"/{id}\")")
                .contains("@DeleteMapping(\"/{id}\")")
                .contains("@OperLog(module = \"订单主表\", action = \"新增\")")
                .contains("public R<Long> create(");
    }

    @Test
    void 导出与列表同一套条件且只取上限内的行() {
        String service = renderer.render("backend/Service.java.ftl",
                GenerationInput.of(model(true, true), richFields()));
        assertThat(service).contains("private static final int EXPORT_LIMIT = 5000;");
        assertThat(service).contains("mapper.selectRows(orderNo, amountStart, amountEnd, payTimeStart, payTimeEnd, status, tags, 0, EXPORT_LIMIT)");
        assertThat(service).contains("CsvUtils.csv(List.of(\"主键\", \"订单号\", \"订单金额\", \"支付时间\", \"状态\"), data)");
    }

    @Test
    void 同一份模型两次渲染逐字节一致() {
        Map<String, Object> model = GenerationInput.of(model(true, true), richFields());
        for (String template : List.of("backend/Entity.java.ftl", "backend/Row.java.ftl",
                "backend/WriteRequest.java.ftl", "backend/Mapper.java.ftl", "backend/Service.java.ftl",
                "backend/Controller.java.ftl")) {
            assertThat(renderer.render(template, model)).as(template).isEqualTo(renderer.render(template, model));
        }
    }

    @Test
    void 生成不出残缺语句的模型当场拦掉() {
        assertThatThrownBy(() -> GenerationInput.of(model(true, true), List.of(
                new Field("id", "主键", "Long", "bigint").primary().autoIncrement().build())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有任何「可录入」字段");

        // 没有主键：行操作、编辑、删除都定位不到记录，拦在生成之前
        assertThatThrownBy(() -> GenerationInput.of(model(true, true), List.of(
                new Field("name", "名称", "String", "varchar").input().build())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有主键字段");
    }
}

package com.scaffold.lowcode.publish;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 发布前的 C 档引用体检（方案 §5.2「C 档引用体检」第 2 条）。
 *
 * <p>挡的是这一件事故：模型删了字段，人写的 {@code {Cls}Extension} 还在引用它。等交付项目编译失败时
 * 人已经在合并请求里了，而 diff 只会显示「生成物变了」，不会告诉他「你少了一个字段」。
 *
 * <p>判据不是「当前模型字段」而是<b>本次发布真正生成的那几个类里声明的成员</b>：审计列与主键由模板补在
 * Row / Entity 上、不在 {@code lc_field} 里，按字段清单判会把 {@code row.getCreateTime()} 报成错引用，
 * 一个天天误报的检查很快就没人当真了。
 */
class ExtensionReferenceCheckTest {

    private static final String ROW = """
            public class SalesOrderRow {
                private Long id;
                private String orderNo;
                private BigDecimal amount;
                private LocalDateTime createTime;

                public Long getId() { return id; }
                public String getOrderNo() { return orderNo; }
            }
            """;

    private static final String ENTITY = """
            public class SalesOrder {
                private Long id;
                private String orderNo;
                private BigDecimal amount;
                private LocalDateTime createTime;
            }
            """;

    @Test
    void 引用已不存在的字段时报出文件行号与字段名() {
        String extension = """
                public class SalesOrderExtension {
                    public void afterRows(List<SalesOrderRow> rows) {
                        rows.forEach(row -> row.setDiscountRate(row.getAmount()));
                    }
                }
                """;

        List<ExtensionReferenceCheck.Stale> stale = ExtensionReferenceCheck
                .scan("service/SalesOrderExtension.java", extension, Set.of(), legal(ROW, ENTITY));

        assertThat(stale).singleElement().satisfies(hit -> {
            assertThat(hit.path()).isEqualTo("service/SalesOrderExtension.java");
            assertThat(hit.line()).isEqualTo(3);
            assertThat(hit.field()).isEqualTo("discountRate");
        });
    }

    @Test
    void 读已不存在的字段同样报() {
        String extension = """
                public class SalesOrderExtension {
                    public void beforeCreate(SalesOrder entity) {
                        if (entity.getDeliveryDate() == null) {
                            throw new BusinessException("先填交货日期");
                        }
                    }
                }
                """;

        assertThat(ExtensionReferenceCheck.scan("x", extension, Set.of(), legal(ROW, ENTITY)))
                .singleElement().satisfies(hit -> {
                    assertThat(hit.line()).isEqualTo(3);
                    assertThat(hit.field()).isEqualTo("deliveryDate");
                });
    }

    @Test
    void 成员直接取字段也要报() {
        // 生成物的字段在 Java 里是 private 的，正常只能走 getter；但同一模块内的 record 访问、
        // 或人把自己写的字段挂上来时就是 .xxx 这种形状，宁多报不漏报
        String extension = """
                public class SalesOrderExtension {
                    public void afterRows(List<SalesOrderRow> rows) {
                        rows.forEach(row -> System.out.println(row.discountRate));
                    }
                }
                """;

        assertThat(ExtensionReferenceCheck.scan("x", extension, Set.of(), legal(ROW, ENTITY)))
                .singleElement().satisfies(hit -> assertThat(hit.field()).isEqualTo("discountRate"));
    }

    @Test
    void 现存字段与模板补的审计列都不该被误报() {
        String extension = """
                public class SalesOrderExtension {
                    public void afterRows(List<SalesOrderRow> rows) {
                        rows.forEach(row -> row.setOrderNo(row.getOrderNo() + "-" + row.getCreateTime()));
                    }
                }
                """;

        assertThat(ExtensionReferenceCheck.scan("x", extension, Set.of(), legal(ROW, ENTITY))).isEmpty();
    }

    @Test
    void 方法调用与类名限定的静态访问不算字段引用() {
        // BigDecimal.setScale、List.size、SalesOrderRow.class、System.out 都不是「某个对象的某个字段」，
        // 把它们的名字都当成模型字段来报，这条检查用两次就没人肯看了
        String extension = """
                public class SalesOrderExtension {
                    public void afterRows(List<SalesOrderRow> rows) {
                        BigDecimal one = rows.get(0).getAmount().setScale(2);
                        Class<?> type = SalesOrderRow.class;
                        if (rows.size() > one.intValue()) {
                            System.out.println(type.name());
                        }
                    }
                }
                """;

        assertThat(ExtensionReferenceCheck.scan("x", extension, Set.of(), legal(ROW, ENTITY))).isEmpty();
    }

    @Test
    void 认不出是模型字段还是别的对象的读取时照文档报出来() {
        // §5.2 第 3 条的口径就是宁多报不漏报：正则级检查认不出 type 是个 JDK 对象。
        // 报出来由人 force 放行，而不是平台猜哪个是假的 —— 这条钉住的是「不静默放过」这个选择本身
        String extension = """
                public class SalesOrderExtension {
                    public void afterRows(List<SalesOrderRow> rows) {
                        Class<?> type = SalesOrderRow.class;
                        System.out.println(type.getName());
                    }
                }
                """;

        assertThat(ExtensionReferenceCheck.scan("x", extension, Set.of(), legal(ROW, ENTITY)))
                .singleElement().satisfies(hit -> assertThat(hit.field()).isEqualTo("name"));
    }

    @Test
    void 人自己在扩展点里加的方法与字段不算错引用() {
        // 「这一行补聚合值」是 §5.2 让人写在这里的正常业务：自己定义的 sumAmount() 不该被当成模型字段
        String extension = """
                public class SalesOrderExtension {
                    private BigDecimal sumAmount;

                    public void afterRows(List<SalesOrderRow> rows) {
                        this.sumAmount = rows.stream().findFirst().map(row -> row.getAmount()).orElse(null);
                        System.out.println(getSumAmount());
                    }

                    public BigDecimal getSumAmount() {
                        return sumAmount;
                    }
                }
                """;

        assertThat(ExtensionReferenceCheck.scan("x", extension, Set.of(), legal(ROW, ENTITY))).isEmpty();
    }

    @Test
    void 生成物之外的白名单符号由调用方给() {
        // 页面还可能引用到本次没生成、但确实存在的东西（前端覆写、别的模型的类），由调用方补进 allowed
        String extension = """
                public class SalesOrderExtension {
                    public void afterRows(List<SalesOrderRow> rows) {
                        rows.forEach(row -> row.getCurrency());
                    }
                }
                """;

        assertThat(ExtensionReferenceCheck.scan("x", extension, Set.of("currency"), legal(ROW, ENTITY))).isEmpty();
        assertThat(ExtensionReferenceCheck.scan("x", extension, Set.of(), legal(ROW, ENTITY)))
                .singleElement().satisfies(hit -> assertThat(hit.field()).isEqualTo("currency"));
    }

    @Test
    void 骨架原文一行引用都没有() {
        // 照模板产出的样子写全：package 与 import 里的包名段长得和「对象.字段」一模一样，
        // 排不掉它们，每一次发布都会被自己这份清单拦下来（这条是 IT 抓出来的）
        String skeleton = """
                package com.scaffold.business.sales_order.service;

                import com.scaffold.business.sales_order.domain.SalesOrder;
                import com.scaffold.business.sales_order.domain.SalesOrderRow;
                import org.springframework.stereotype.Component;

                import java.util.List;

                @Component
                public class SalesOrderExtension {

                    /** 列表与导出的行取完之后。补聚合值、把 code 换成中文都在这里做。 */
                    public void afterRows(List<SalesOrderRow> rows) {
                        // TODO 人工填写：平台只在首次发布生成本类，之后不再覆盖
                    }

                    public void beforeCreate(SalesOrder entity) {
                        // TODO 人工填写
                    }
                }
                """;

        assertThat(ExtensionReferenceCheck.scan("x", skeleton, Set.of(), legal(ROW, ENTITY))).isEmpty();
    }

    @Test
    void 骨架之外人真的写了业务时也认得出那一条错引用() {
        String withBusiness = """
                package com.scaffold.business.sales_order.service;

                import com.scaffold.business.sales_order.domain.SalesOrderRow;
                import java.util.List;

                public class SalesOrderExtension {
                    public void afterRows(List<SalesOrderRow> rows) {
                        rows.forEach(row -> row.setOrderNo(row.getDeliveryDate() + "|" + row.getOrderNo()));
                    }
                }
                """;

        assertThat(ExtensionReferenceCheck.scan("x", withBusiness, Set.of(), legal(ROW, ENTITY)))
                .singleElement().satisfies(hit -> {
                    assertThat(hit.line()).isEqualTo(8);
                    assertThat(hit.field()).isEqualTo("deliveryDate");
                });
    }

    /** 本次发布真正生成的那几个类里声明的成员：主键与审计列也在里面，它们不在 lc_field 里但确实能引用 */
    private static Set<String> legal(String... sources) {
        return ExtensionReferenceCheck.declaredMembers(sources);
    }
}

package com.scaffold.lowcode.model;

import com.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 生成物标识符守卫（PR 5B）。
 *
 * <p>生成的 SQL 不给表名与列名加反引号，所以保留字必须在建模时就拦住 ——
 * 放过去的代价是交付项目运行期才报语法错，而那时人已经在排查数据库了。
 * 判定是整词相等：{@code order_no} 这种派生名合法，{@code order} 不合法。
 */
class SqlIdentifiersTest {

    @Test
    void 保留字按整词判定且大小写无关() {
        assertThat(SqlIdentifiers.isReserved("order")).isTrue();
        assertThat(SqlIdentifiers.isReserved("DESC")).isTrue();
        assertThat(SqlIdentifiers.isReserved(" group ")).isTrue();
        assertThat(SqlIdentifiers.isReserved("order_no")).isFalse();
        assertThat(SqlIdentifiers.isReserved("description")).isFalse();
        assertThat(SqlIdentifiers.isReserved("sort_order")).isFalse();
        // status / type / value 这类常见业务字段名在 MySQL 里不是保留字，不该误伤
        assertThat(SqlIdentifiers.isReserved("status")).isFalse();
        assertThat(SqlIdentifiers.isReserved("type")).isFalse();
        assertThat(SqlIdentifiers.isReserved(null)).isFalse();
    }

    @Test
    void 列名被拦时报错带着能照着改的建议() {
        assertThatThrownBy(() -> SqlIdentifiers.requireColumnName("desc"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MySQL 保留字")
                .hasMessageContaining("description");
        assertThatThrownBy(() -> SqlIdentifiers.requireColumnName("key"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("biz_key");
        assertThatCode(() -> SqlIdentifiers.requireColumnName("remark")).doesNotThrowAnyException();
    }

    @Test
    void 表名被拦时给出加前缀的出路() {
        assertThatThrownBy(() -> SqlIdentifiers.requireTableName("order"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("biz_order");
        assertThatCode(() -> SqlIdentifiers.requireTableName("biz_order")).doesNotThrowAnyException();
    }
}

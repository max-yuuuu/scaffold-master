package com.scaffold.common.export;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 统一 CSV 拼装（所有列表导出走这里，转义与表头只有一份实现）。
 */
class CsvUtilsTest {

    @Test
    void 时间值按平台那两种规范串写出() {
        // 生成物的列表行里日期是 LocalDate / LocalDateTime（JSON 出参由 @JsonFormat 钉成规范串），
        // 而 CSV 这一路是直接把值转字符串的：不在这儿收口径，同一个值在页面上是「2026-11-04 20:43:49」、
        // 在导出的文件里就变成 ISO 的「2026-11-04T20:43:49」，拿文件对数的人会遇到两种写法
        String csv = CsvUtils.csv(
                List.of("交货日期", "创建时间"),
                List.of(CsvUtils.row(LocalDate.of(2026, 12, 1), LocalDateTime.of(2026, 11, 4, 20, 43, 49))));

        assertThat(csv).isEqualTo("交货日期,创建时间\n2026-12-01,2026-11-04 20:43:49\n");
    }

    @Test
    void 逗号引号与换行按CSV规则转义() {
        String csv = CsvUtils.csv(
                List.of("备注", "占位"),
                List.of(CsvUtils.row("含,逗号", "1"), CsvUtils.row("含\"引号\"", "2"),
                        CsvUtils.row(null, "3"), CsvUtils.row("第二行\n换行", "4")));

        assertThat(csv).isEqualTo("备注,占位\n\"含,逗号\",1\n\"含\"\"引号\"\"\",2\n,3\n\"第二行\n换行\",4\n");
    }
}

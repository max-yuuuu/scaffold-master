package com.scaffold.lowcode.publish.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
 * 页面版本（lc_page_version）。带 tenant_id 且已登记进租户拦截器白名单，条件自动注入。
 *
 * <p>只在「推送成功且 tag 已落到远端」之后写行：这行记录的是仓库里真存在的那一版，
 * 库里说发过而仓库里查无此提交，PR 9 的回滚就会取到不存在的内容。
 */
@Mapper
public interface LcPageVersionMapper {

    /**
     * @param row pageId / version / designSnapshot / filesSnapshot / commitSha / changeDesc（列名同库）
     */
    @Insert("""
            INSERT INTO lc_page_version (page_id, version, design_snapshot, files_snapshot, commit_sha,
                                         change_desc, create_by)
            VALUES (#{row.pageId}, #{row.version}, #{row.designSnapshot}, #{row.filesSnapshot}, #{row.commitSha},
                    #{row.changeDesc}, #{operator})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insert(@Param("row") Map<String, Object> row, @Param("operator") String operator);
}

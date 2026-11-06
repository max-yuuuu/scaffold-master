package com.scaffold.lowcode.publish.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
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

    /**
     * 某一页的版本历史，最新的一版在最前 —— 回滚的入口就是「退回到上一版」，倒序才让那一行落在手边。
     *
     * <p>tag 与动作类型不在本表（V9 把它们记在 {@code lc_publish_log}，那里才是动作台账），所以这里
     * 只查单表、由服务按版本号把那两格补上。用派生表 join 也能一条 SQL 拿全，但租户拦截器要按表注入
     * 条件，语句越绕越容易在解析这一步出事；两条简单 SQL 换的是「拦截器一定认得出租户条件」。
     */
    @Select("""
            SELECT id, page_id AS pageId, version, commit_sha AS commitSha, change_desc AS changeDesc,
                   create_by AS createBy, create_time AS createTime,
                   JSON_LENGTH(files_snapshot) AS fileCount
            FROM lc_page_version
            WHERE page_id = #{pageId}
            ORDER BY version DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> selectVersions(@Param("pageId") long pageId, @Param("limit") int limit,
                                             @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM lc_page_version WHERE page_id = #{pageId}")
    long countVersions(@Param("pageId") long pageId);

    /**
     * 取某一版：回滚的取数就照着这一行的 {@code commit_sha} 与 {@code files_snapshot} 走，
     * 所以这两列必须一起回来 —— 少了快照就没法判断「这个文件当时归谁」。
     */
    @Select("""
            SELECT id, page_id AS pageId, version, commit_sha AS commitSha, change_desc AS changeDesc,
                   create_by AS createBy, create_time AS createTime,
                   files_snapshot AS filesSnapshot, design_snapshot AS designSnapshot
            FROM lc_page_version
            WHERE page_id = #{pageId} AND version = #{version}
            """)
    Map<String, Object> selectVersion(@Param("pageId") long pageId, @Param("version") int version);
}

package com.scaffold.lowcode.publish.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
 * 发布记录（lc_publish_log）。带 tenant_id 且已登记进租户拦截器白名单，条件自动注入。
 *
 * <p>与 lc_page_version 的分工：日志是「每一次点发布都有一条」（含无变更与失败），版本行只在成功时有。
 * 失败也必须留一条 —— 「库里说这个项目今天发布过三次，仓库里只有两个版本」这种状态，只有靠日志才说得清
 * 是哪一次的推送没出去。
 */
@Mapper
public interface LcPublishLogMapper {

    /**
     * @param row pageId / projectId / version / publishType / publishStatus / filesGenerated /
     *            commitSha / tagName / branch / errorMessage（列名同库）
     */
    @Insert("""
            INSERT INTO lc_publish_log (project_id, page_id, version, publish_type, publish_status,
                                        files_generated, commit_sha, tag_name, branch, error_message, publish_by)
            VALUES (#{row.projectId}, #{row.pageId}, #{row.version}, #{row.publishType}, #{row.publishStatus},
                    #{row.filesGenerated}, #{row.commitSha}, #{row.tagName}, #{row.branch}, #{row.errorMessage},
                    #{operator})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insert(@Param("row") Map<String, Object> row, @Param("operator") String operator);
}

package com.scaffold.lowcode.publish.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
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

    /**
     * 这几版各自是哪一次成功的动作产出来的：版本行只说「有这一版」，而 tag 名与动作类型记在台账里。
     *
     * <p>只取 {@code publish_status = 1}：同一版可能先推送失败、重试才成功，失败那几条不该出现在
     * 人的版本历史里（它们没有 tag，也没有可回滚的锚点）。{@code versions} 为空时调用方别进来 ——
     * {@code IN ()} 是无效 SQL。
     */
    @Select("""
            <script>
            SELECT version, tag_name AS tagName, publish_type AS publishType
            FROM lc_publish_log
            WHERE page_id = #{pageId} AND publish_status = 1
              AND version IN
              <foreach collection="versions" item="v" open="(" separator="," close=")">#{v}</foreach>
            ORDER BY id
            </script>
            """)
    List<Map<String, Object>> selectSuccessActions(@Param("pageId") long pageId,
                                                   @Param("versions") List<Integer> versions);
}

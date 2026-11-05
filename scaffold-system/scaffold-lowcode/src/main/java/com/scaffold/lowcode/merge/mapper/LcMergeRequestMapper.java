package com.scaffold.lowcode.merge.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 合并请求表访问。两张表都带 tenant_id 且已登记进租户拦截器白名单，条件自动注入，插入不写这一列。
 *
 * <p>列表与详情都不带文件清单：一次大重构能有几百个文件，一屏请求各挂一份清单会把内存吃光，
 * 而列表上只需要「几个文件、几个冲突」——那两个数按 {@link LcMergeRequestFileMapper} 现算，
 * 不存冗余计数（存了就会和清单打架）。
 */
@Mapper
public interface LcMergeRequestMapper {

    @Select("""
            <script>
            SELECT r.id, r.project_id AS projectId, r.title, r.description,
                   r.source_branch AS sourceBranch, r.target_branch AS targetBranch,
                   r.source_sha AS sourceSha, r.target_sha AS targetSha, r.merge_base_sha AS mergeBaseSha,
                   r.status, r.check_status AS checkStatus, r.check_message AS checkMessage,
                   r.check_time AS checkTime, r.merged_by AS mergedBy,
                   r.merged_time AS mergedTime,
                   r.create_by AS createBy, r.create_time AS createTime, r.update_time AS updateTime,
                   p.project_name AS projectName, p.owner_user_id AS ownerUserId,
                   (SELECT COUNT(*) FROM lc_merge_request_file f WHERE f.request_id = r.id) AS fileCount,
                   (SELECT COALESCE(SUM(f.is_conflict), 0) FROM lc_merge_request_file f
                      WHERE f.request_id = r.id) AS conflictCount
            FROM lc_merge_request r
            JOIN lc_project p ON p.id = r.project_id AND p.deleted = 0
            WHERE 1 = 1
            <if test="projectId != null">AND r.project_id = #{projectId}</if>
            <!-- 「待我审核」按项目负责人过滤；不限负责人时看得到全部 -->
            <if test="ownerUserId != null">AND p.owner_user_id = #{ownerUserId}</if>
            ORDER BY r.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> selectRequests(@Param("projectId") Long projectId,
                                             @Param("ownerUserId") Long ownerUserId,
                                             @Param("limit") int limit,
                                             @Param("offset") long offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM lc_merge_request r
            JOIN lc_project p ON p.id = r.project_id AND p.deleted = 0
            WHERE 1 = 1
            <if test="projectId != null">AND r.project_id = #{projectId}</if>
            <if test="ownerUserId != null">AND p.owner_user_id = #{ownerUserId}</if>
            </script>
            """)
    long countRequests(@Param("projectId") Long projectId, @Param("ownerUserId") Long ownerUserId);

    @Select("""
            SELECT id, project_id AS projectId, title, description,
                   source_branch AS sourceBranch, target_branch AS targetBranch,
                   source_sha AS sourceSha, target_sha AS targetSha, merge_base_sha AS mergeBaseSha,
                   status, check_status AS checkStatus, check_message AS checkMessage,
                   check_time AS checkTime, merged_by AS mergedBy, merged_time AS mergedTime,
                   create_by AS createBy, create_time AS createTime,
                   update_time AS updateTime
            FROM lc_merge_request
            WHERE id = #{id}
            """)
    Map<String, Object> selectRequest(@Param("id") Long id);

    /**
     * 记一笔已合并：状态、合交人、合交时间一次写定。
     *
     * <p>{@code WHERE status = 0} 不是多余的防御 —— 两个人同时点合并时，第二个人必须发现这条已经收口，
     * 而不是把 merged_by 改写成自己、把两次合并说成一件事。
     */
    @Update("""
            UPDATE lc_merge_request
            SET status = 2, merged_by = #{operator}, merged_time = NOW(), update_by = #{operator},
                check_message = #{message}
            WHERE id = #{id} AND status = 0
            """)
    int updateMerged(@Param("id") Long id, @Param("message") String message, @Param("operator") String operator);

    /**
     * 同一分支对只留一条还活着的请求（待审或陈旧）。
     *
     * <p>已经在主干上的内容不该再挂一条新的：每次刷新都新开一条，列表就会被同一次发布刷出好几条重复请求。
     */
    @Select("""
            SELECT id, project_id AS projectId, title, source_branch AS sourceBranch,
                   target_branch AS targetBranch, source_sha AS sourceSha, target_sha AS targetSha,
                   merge_base_sha AS mergeBaseSha, status, check_status AS checkStatus
            FROM lc_merge_request
            WHERE project_id = #{projectId} AND source_branch = #{sourceBranch}
              AND target_branch = #{targetBranch} AND status IN (0, 1)
            ORDER BY id DESC
            LIMIT 1
            """)
    Map<String, Object> selectOpenRequest(@Param("projectId") Long projectId,
                                          @Param("sourceBranch") String sourceBranch,
                                          @Param("targetBranch") String targetBranch);

    @Insert("""
            INSERT INTO lc_merge_request (project_id, title, description, source_branch, target_branch,
                                          source_sha, target_sha, merge_base_sha,
                                          status, check_status, check_message, check_time, create_by, update_by)
            VALUES (#{row.projectId}, #{row.title}, #{row.description}, #{row.sourceBranch}, #{row.targetBranch},
                    #{row.sourceSha}, #{row.targetSha}, #{row.mergeBaseSha},
                    #{row.status}, #{row.checkStatus}, #{row.checkMessage}, NOW(), #{operator}, #{operator})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insert(@Param("row") Map<String, Object> row, @Param("operator") String operator);

    /** 重算就是就地覆盖这一条：提交号、结论、清单一起换，历史留给已合并与已关闭的那些行 */
    @Update("""
            UPDATE lc_merge_request
            SET title = #{row.title}, source_sha = #{row.sourceSha}, target_sha = #{row.targetSha},
                merge_base_sha = #{row.mergeBaseSha}, status = #{row.status},
                check_status = #{row.checkStatus}, check_message = #{row.checkMessage},
                check_time = NOW(), update_by = #{operator}
            WHERE id = #{row.id}
            """)
    int updateOnRefresh(@Param("row") Map<String, Object> row, @Param("operator") String operator);

    /**
     * 目标分支前进就把这条请求标成陈旧。
     *
     * <p>只改状态与说明，不动那三个提交号 —— 它们记的是「这条请求算的是哪一对提交」，
     * 改了就不是这条请求算过的那件事了。
     */
    @Update("""
            UPDATE lc_merge_request
            SET status = 1, check_message = #{message}, update_by = #{operator}
            WHERE id = #{id} AND status = 0
            """)
    int markStale(@Param("id") Long id, @Param("message") String message, @Param("operator") String operator);
}

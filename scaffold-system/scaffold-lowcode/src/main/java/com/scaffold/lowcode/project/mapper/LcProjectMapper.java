package com.scaffold.lowcode.project.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 项目表访问。lc_project 带 tenant_id 且已登记进租户拦截器白名单，
 * 所以这里不写 tenant_id 条件，也不许标 {@code @InterceptorIgnore}。
 */
@Mapper
public interface LcProjectMapper {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM lc_project p
            WHERE p.deleted = 0
              AND (#{keyword} IS NULL OR #{keyword} = ''
                   OR p.project_code LIKE CONCAT('%', #{keyword}, '%')
                   OR p.project_name LIKE CONCAT('%', #{keyword}, '%'))
              AND (#{status} IS NULL OR p.status = #{status})
            </script>
            """)
    long countProjects(@Param("keyword") String keyword, @Param("status") Integer status);

    @Select("""
            <script>
            SELECT p.id,
                   p.project_code AS projectCode,
                   p.project_name AS projectName,
                   p.project_desc AS projectDesc,
                   p.repo_url AS repoUrl,
                   p.main_branch AS mainBranch,
                   p.gen_branch AS genBranch,
                   p.owner_user_id AS ownerUserId,
                   (SELECT u.nickname FROM sys_user u WHERE u.id = p.owner_user_id) AS ownerName,
                   p.workspace_path AS workspacePath,
                   p.base_scaffold_sha AS baseScaffoldSha,
                   p.last_commit_sha AS lastCommitSha,
                   p.sync_status AS syncStatus,
                   p.sync_message AS syncMessage,
                   DATE_FORMAT(p.init_time, '%Y-%m-%d %H:%i') AS initTime,
                   DATE_FORMAT(p.last_publish_time, '%Y-%m-%d %H:%i') AS lastPublishTime,
                   p.status,
                   DATE_FORMAT(p.create_time, '%Y-%m-%d %H:%i') AS createTime,
                   p.remark
            FROM lc_project p
            WHERE p.deleted = 0
              AND (#{keyword} IS NULL OR #{keyword} = ''
                   OR p.project_code LIKE CONCAT('%', #{keyword}, '%')
                   OR p.project_name LIKE CONCAT('%', #{keyword}, '%'))
              AND (#{status} IS NULL OR p.status = #{status})
            ORDER BY p.id
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<Map<String, Object>> selectProjects(@Param("keyword") String keyword,
                                             @Param("status") Integer status,
                                             @Param("offset") int offset,
                                             @Param("pageSize") int pageSize);

    /** 项目编码在租户内唯一（uk_lc_project_code），租户条件由拦截器补 */
    @Select("""
            SELECT COUNT(*)
            FROM lc_project
            WHERE project_code = #{projectCode}
              AND deleted = 0
              AND (#{excludeId} IS NULL OR id <> #{excludeId})
            """)
    long countProjectCode(@Param("projectCode") String projectCode, @Param("excludeId") Long excludeId);

    @Select("SELECT id, project_code AS projectCode, project_name AS projectName, repo_url AS repoUrl FROM lc_project WHERE id = #{id} AND deleted = 0")
    Map<String, Object> selectProjectBrief(@Param("id") Long id);

    /** 仓库与工作区动作要用的全部字段：分支名、当前状态、已有路径与提交号 */
    @Select("""
            SELECT id,
                   project_code AS projectCode,
                   project_name AS projectName,
                   repo_url AS repoUrl,
                   main_branch AS mainBranch,
                   gen_branch AS genBranch,
                   owner_user_id AS ownerUserId,
                   workspace_path AS workspacePath,
                   base_scaffold_sha AS baseScaffoldSha,
                   last_commit_sha AS lastCommitSha,
                   sync_status AS syncStatus,
                   status
            FROM lc_project
            WHERE id = #{id} AND deleted = 0
            """)
    Map<String, Object> selectProjectForSync(@Param("id") Long id);

    /**
     * 推进项目生命周期状态。空值一律不覆盖已有列，所以「只改状态与说明」不会把 workspace_path
     * 或提交号抹平；{@code markInit} 为真时写 init_time。
     */
    @Update("""
            <script>
            UPDATE lc_project
            SET sync_status = #{syncStatus},
                sync_message = #{message},
                last_sync_time = NOW()
                <if test="workspacePath != null">, workspace_path = #{workspacePath}</if>
                <if test="baseScaffoldSha != null">, base_scaffold_sha = #{baseScaffoldSha}</if>
                <if test="lastCommitSha != null">, last_commit_sha = #{lastCommitSha}</if>
                <if test="markInit">, init_time = NOW()</if>
                , update_by = #{operator}
            WHERE id = #{id} AND deleted = 0
            </script>
            """)
    int updateSyncState(@Param("id") Long id,
                        @Param("syncStatus") int syncStatus,
                        @Param("message") String message,
                        @Param("workspacePath") String workspacePath,
                        @Param("baseScaffoldSha") String baseScaffoldSha,
                        @Param("lastCommitSha") String lastCommitSha,
                        @Param("markInit") boolean markInit,
                        @Param("operator") String operator);

    /**
     * 负责人必须是本租户一个还在用的账号。sys_user 在租户拦截器白名单里，所以这条天然带租户条件 ——
     * 不校验的话就会把别租户的用户 ID 指成本项目负责人，而「待我审核」与合并权限都按它判。
     */
    @Select("SELECT COUNT(*) FROM sys_user WHERE id = #{userId} AND deleted = 0 AND status = 1")
    long countUsableUser(@Param("userId") Long userId);

    /**
     * 是否真正拥有平台超管身份：角色码 {@code super_admin} <b>且账号归属平台租户</b>。
     *
     * <p>与 {@code PlatformBoundary.isSuperAdmin} 同一判据 —— 那边要 authMapper 的角色查询，
     * 低代码模块没有它，而合并这条授权必须在服务层兑现。租户内自建一个同名角色不算数，
     * 否则任何租户的管理员都能把自己项目的清单推进主干。
     */
    @Select("""
            <script>
            SELECT COUNT(*) FROM sys_user u
            JOIN sys_user_role ur ON ur.user_id = u.id
            JOIN sys_role r ON r.id = ur.role_id
            WHERE u.id = #{userId} AND u.deleted = 0 AND u.tenant_id = #{platformTenantId}
              AND r.role_code = 'super_admin'
            </script>
            """)
    long countPlatformSuperAdmin(@Param("userId") Long userId, @Param("platformTenantId") long platformTenantId);

    @Insert("""
            INSERT INTO lc_project (project_code, project_name, project_desc, repo_url, main_branch, gen_branch,
                                    owner_user_id,
                                    sync_status, status, remark, create_by, update_by)
            VALUES (#{row.projectCode}, #{row.projectName}, #{row.projectDesc}, #{row.repoUrl}, #{row.mainBranch}, #{row.genBranch},
                    #{row.ownerUserId},
                    0, #{row.status}, #{row.remark}, #{operator}, #{operator})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insertProject(@Param("row") Map<String, Object> row, @Param("operator") String operator);

    @Update("""
            UPDATE lc_project
            SET project_name = COALESCE(#{row.projectName}, project_name),
                project_desc = COALESCE(#{row.projectDesc}, project_desc),
                repo_url = COALESCE(#{row.repoUrl}, repo_url),
                main_branch = COALESCE(#{row.mainBranch}, main_branch),
                gen_branch = COALESCE(#{row.genBranch}, gen_branch),
                owner_user_id = COALESCE(#{row.ownerUserId}, owner_user_id),
                status = COALESCE(#{row.status}, status),
                remark = COALESCE(#{row.remark}, remark),
                update_by = #{operator}
            WHERE id = #{id} AND deleted = 0
            """)
    int updateProject(@Param("id") Long id, @Param("row") Map<String, Object> row, @Param("operator") String operator);

    @Update("""
            UPDATE lc_project
            SET status = #{status}, update_by = #{operator}
            WHERE id = #{id} AND deleted = 0
            """)
    int updateProjectStatus(@Param("id") Long id, @Param("status") Integer status, @Param("operator") String operator);

    @Update("""
            UPDATE lc_project
            SET deleted = 1, update_by = #{operator}
            WHERE id = #{id} AND deleted = 0
            """)
    int softDeleteProject(@Param("id") Long id, @Param("operator") String operator);

    /**
     * 页面发布成功之后推进项目的读数。
     *
     * <p>不复用 {@link #updateSyncState} —— 那个方法的语义是「仓库生命周期变了」，它会把 sync_status
     * 一起改掉；发布一次页面不该把「就绪」改成别的状态，也不该被写成一条同步说明。
     */
    @Update("""
            UPDATE lc_project
            SET last_commit_sha = #{commitSha}, last_publish_time = NOW(), update_by = #{operator}
            WHERE id = #{id} AND deleted = 0
            """)
    int markPublished(@Param("id") Long id, @Param("commitSha") String commitSha, @Param("operator") String operator);

    /**
     * 推进「这个项目当初交付在哪个底座上」（方案 §5.2）：升级合进主干后必须跟着走，
     * 否则下一次三方合并的 base 侧还是上一轮那一版，会把已经下发过的内容再下发一遍。
     *
     * <p>单独一条而不复用 {@link #markPublished}：那个记的是「最后一次推送的提交」，
     * 这里记的是「底座版本」，两个事实各有读者。
     */
    @Update("""
            UPDATE lc_project
            SET base_scaffold_sha = #{baseScaffoldSha}, update_by = #{operator}
            WHERE id = #{id} AND deleted = 0
            """)
    int updateBaseScaffold(@Param("id") Long id, @Param("baseScaffoldSha") String baseScaffoldSha,
                           @Param("operator") String operator);
}

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
                   p.base_scaffold_sha AS baseScaffoldSha,
                   p.last_commit_sha AS lastCommitSha,
                   p.sync_status AS syncStatus,
                   CASE p.sync_status
                       WHEN 0 THEN '未初始化'
                       WHEN 1 THEN '初始化中'
                       WHEN 2 THEN '就绪'
                       WHEN 3 THEN '工作区脏'
                       WHEN 4 THEN '与远端分叉'
                       ELSE '仓库不可达'
                   END AS syncStatusText,
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

    @Insert("""
            INSERT INTO lc_project (project_code, project_name, project_desc, repo_url, main_branch, gen_branch,
                                    sync_status, status, remark, create_by, update_by)
            VALUES (#{row.projectCode}, #{row.projectName}, #{row.projectDesc}, #{row.repoUrl}, #{row.mainBranch}, #{row.genBranch},
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
}

package com.scaffold.lowcode.project.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * 底座升级结论（lc_base_upgrade）。带 tenant_id 且已登记进租户拦截器白名单，条件自动注入。
 *
 * <p>一次发起一条。逐文件结论存成 JSON 而不是另开一张明细表：它只在「这次升级怎么样了」
 * 那一屏里整份读出来用，没有任何地方按文件查它 —— 为一份没人查的清单建表，换来的是每次读都要 join。
 */
@Mapper
public interface LcBaseUpgradeMapper {

    /**
     * @param row projectId / baseScaffoldSha / targetScaffoldSha / upgradeBranch / filesDetail /
     *            status / message（列名同库）
     */
    @Insert("""
            INSERT INTO lc_base_upgrade (project_id, base_scaffold_sha, target_scaffold_sha, upgrade_branch,
                                         files_detail, status, message, create_by)
            VALUES (#{row.projectId}, #{row.baseScaffoldSha}, #{row.targetScaffoldSha}, #{row.upgradeBranch},
                    #{row.filesDetail}, #{row.status}, #{row.message}, #{operator})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insert(@Param("row") Map<String, Object> row, @Param("operator") String operator);

    /** 最近一次升级结论：项目列表与升级结果屏都只读这一条，历史列表属 10D */
    @Select("""
            SELECT id, project_id AS projectId, base_scaffold_sha AS baseScaffoldSha,
                   target_scaffold_sha AS targetScaffoldSha, upgrade_branch AS upgradeBranch,
                   files_detail AS filesDetail, status, message, create_by AS createBy, create_time AS createTime
            FROM lc_base_upgrade
            WHERE project_id = #{projectId}
            ORDER BY id DESC
            LIMIT 1
            """)
    Map<String, Object> selectLatest(@Param("projectId") long projectId);
}

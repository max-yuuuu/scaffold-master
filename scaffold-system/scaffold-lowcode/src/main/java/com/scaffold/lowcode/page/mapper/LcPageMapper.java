package com.scaffold.lowcode.page.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 页面表访问。lc_page 带 tenant_id 且已登记进租户拦截器白名单，条件自动注入。
 *
 * <p>列表不查 {@code design_json}：一屏几十行页面各带一份完整设计，翻页就把内存吃光了，
 * 而列表上没有一处要读它。设计只在详情里给。
 */
@Mapper
public interface LcPageMapper {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM lc_page p
            WHERE p.deleted = 0
              AND (#{projectId} IS NULL OR p.project_id = #{projectId})
              AND (#{modelId} IS NULL OR p.model_id = #{modelId})
              AND (#{keyword} IS NULL OR #{keyword} = ''
                   OR p.page_code LIKE CONCAT('%', #{keyword}, '%')
                   OR p.page_name LIKE CONCAT('%', #{keyword}, '%'))
              AND (#{status} IS NULL OR p.status = #{status})
            </script>
            """)
    long countPages(@Param("projectId") Long projectId, @Param("modelId") Long modelId,
                    @Param("keyword") String keyword, @Param("status") Integer status);

    @Select("""
            <script>
            SELECT p.id,
                   p.project_id AS projectId,
                   p.model_id AS modelId,
                   p.page_code AS pageCode,
                   p.page_name AS pageName,
                   p.page_desc AS pageDesc,
                   p.page_type AS pageType,
                   p.route_path AS routePath,
                   p.menu_icon AS menuIcon,
                   p.menu_sort AS menuSort,
                   p.status,
                   m.model_code AS modelCode,
                   m.model_name AS modelName,
                   pr.project_name AS projectName,
                   JSON_LENGTH(p.ref_fields) AS refFieldCount,
                   DATE_FORMAT(p.create_time, '%Y-%m-%d %H:%i') AS createTime,
                   DATE_FORMAT(p.update_time, '%Y-%m-%d %H:%i') AS updateTime
            FROM lc_page p
            LEFT JOIN lc_model m ON m.id = p.model_id
            LEFT JOIN lc_project pr ON pr.id = p.project_id
            WHERE p.deleted = 0
              AND (#{projectId} IS NULL OR p.project_id = #{projectId})
              AND (#{modelId} IS NULL OR p.model_id = #{modelId})
              AND (#{keyword} IS NULL OR #{keyword} = ''
                   OR p.page_code LIKE CONCAT('%', #{keyword}, '%')
                   OR p.page_name LIKE CONCAT('%', #{keyword}, '%'))
              AND (#{status} IS NULL OR p.status = #{status})
            ORDER BY p.project_id, p.id
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<Map<String, Object>> selectPages(@Param("projectId") Long projectId, @Param("modelId") Long modelId,
                                          @Param("keyword") String keyword, @Param("status") Integer status,
                                          @Param("offset") int offset, @Param("pageSize") int pageSize);

    /** 详情：这一条要带设计原文，前端设计器吃的就是它 */
    @Select("""
            SELECT p.id,
                   p.project_id AS projectId,
                   p.model_id AS modelId,
                   p.page_code AS pageCode,
                   p.page_name AS pageName,
                   p.page_desc AS pageDesc,
                   p.page_type AS pageType,
                   p.route_path AS routePath,
                   p.menu_icon AS menuIcon,
                   p.menu_sort AS menuSort,
                   p.design_json AS design,
                   p.ref_fields AS refFields,
                   p.status,
                   m.model_code AS modelCode,
                   m.model_name AS modelName,
                   DATE_FORMAT(p.create_time, '%Y-%m-%d %H:%i') AS createTime,
                   DATE_FORMAT(p.update_time, '%Y-%m-%d %H:%i') AS updateTime
            FROM lc_page p
            LEFT JOIN lc_model m ON m.id = p.model_id
            WHERE p.id = #{id}
              AND p.deleted = 0
            """)
    Map<String, Object> selectPageBrief(@Param("id") Long id);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM lc_page
            WHERE project_id = #{projectId}
              AND page_code = #{pageCode}
              AND deleted = 0
              AND (#{excludeId} IS NULL OR id &lt;&gt; #{excludeId})
            </script>
            """)
    long countPageCode(@Param("projectId") Long projectId, @Param("pageCode") String pageCode,
                       @Param("excludeId") Long excludeId);

    /**
     * 某个模型下未删除的页面及其引用清单。
     *
     * <p>删字段与下线模型前的反查用它：{@code ref_fields} 是 JSON 列，按 MySQL 版本不同
     * {@code JSON_CONTAINS} 的写法与返回值不稳，而且要把「哪几个页面在用」这个名字报给人看，
     * 取回来在 Java 里判更省事 —— 一个模型下的页面本来就是几十量级。
     */
    @Select("""
            SELECT id, page_code AS pageCode, page_name AS pageName, ref_fields AS refFields
            FROM lc_page
            WHERE model_id = #{modelId}
              AND deleted = 0
            ORDER BY id
            """)
    List<Map<String, Object>> selectPagesOfModel(@Param("modelId") Long modelId);

    @Select("SELECT COUNT(*) FROM lc_page WHERE model_id = #{modelId} AND deleted = 0")
    long countPagesOfModel(@Param("modelId") Long modelId);

    /**
     * 同一个模型上已经有的列表页。
     *
     * <p>第一期生成物的页面落点是 {@code views/{module}/{Cls}.vue}，由模型类名派生：同一模型的第二个
     * 列表页会在下一次发布整文件盖掉第一个。§1.5 说的「一个模型多个页面」是列表 + 详情 + 表单三类，
     * 第一期只落了列表这一类，所以按「模型 × 列表页」唯一拦下来。
     */
    @Select("""
            <script>
            SELECT id, page_code AS pageCode, page_name AS pageName
            FROM lc_page
            WHERE model_id = #{modelId}
              AND page_type = 'list'
              AND deleted = 0
              AND (#{excludeId} IS NULL OR id &lt;&gt; #{excludeId})
            ORDER BY id
            </script>
            """)
    List<Map<String, Object>> selectListPagesOfModel(@Param("modelId") Long modelId, @Param("excludeId") Long excludeId);

    @Select("SELECT COUNT(*) FROM lc_page WHERE project_id = #{projectId} AND deleted = 0")
    long countPagesOfProject(@Param("projectId") Long projectId);

    /**
     * 发布要吃的页面行：除了设计原文，还要当前版本号与最近一次发布结果。
     *
     * <p>不复用 {@link #selectPageBrief(Long)} 是因为那份是给设计器读的，它不带发布四列；
     * 而发布这条链路每次都要按 {@code current_version} 算下一个版本号，缺了它就得再查一次表。
     */
    @Select("""
            SELECT p.id,
                   p.project_id AS projectId,
                   p.model_id AS modelId,
                   p.page_code AS pageCode,
                   p.page_name AS pageName,
                   p.route_path AS routePath,
                   p.menu_icon AS menuIcon,
                   p.menu_sort AS menuSort,
                   p.design_json AS design,
                   p.status,
                   p.current_version AS currentVersion,
                   p.publish_status AS publishStatus,
                   m.model_code AS modelCode
            FROM lc_page p
            LEFT JOIN lc_model m ON m.id = p.model_id
            WHERE p.id = #{id}
              AND p.deleted = 0
            """)
    Map<String, Object> selectPageForPublish(@Param("id") Long id);

    /**
     * 推进页面的发布状态。{@code version} 传 null 表示这次动作不认新版本号（无变更与失败）。
     */
    @Update("""
            <script>
            UPDATE lc_page
            SET publish_status = #{status},
                last_publish_time = NOW(),
                publish_message = #{message}
                <if test="version != null">, current_version = #{version}</if>
                , update_by = #{operator}
            WHERE id = #{id} AND deleted = 0
            </script>
            """)
    int updatePublishState(@Param("id") Long id, @Param("version") Integer version,
                           @Param("status") int status, @Param("message") String message,
                           @Param("operator") String operator);

    @Insert("""
            INSERT INTO lc_page (project_id, model_id, page_code, page_name, page_desc, page_type,
                                 route_path, menu_icon, menu_sort, design_json, ref_fields,
                                 status, create_by, update_by)
            VALUES (#{row.projectId}, #{row.modelId}, #{row.pageCode}, #{row.pageName}, #{row.pageDesc}, #{row.pageType},
                    #{row.routePath}, #{row.menuIcon}, #{row.menuSort}, #{row.designJson}, #{row.refFields},
                    #{row.status}, #{operator}, #{operator})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insertPage(@Param("row") Map<String, Object> row, @Param("operator") String operator);

    @Update("""
            UPDATE lc_page
            SET page_name = #{row.pageName},
                page_desc = #{row.pageDesc},
                page_type = #{row.pageType},
                route_path = #{row.routePath},
                menu_icon = #{row.menuIcon},
                menu_sort = #{row.menuSort},
                design_json = #{row.designJson},
                ref_fields = #{row.refFields},
                status = COALESCE(#{row.status}, status),
                update_by = #{operator}
            WHERE id = #{id}
              AND deleted = 0
            """)
    int updatePage(@Param("id") Long id, @Param("row") Map<String, Object> row, @Param("operator") String operator);

    @Update("UPDATE lc_page SET status = #{status}, update_by = #{operator} WHERE id = #{id} AND deleted = 0")
    int updatePageStatus(@Param("id") Long id, @Param("status") Integer status, @Param("operator") String operator);

    @Update("UPDATE lc_page SET deleted = 1, update_by = #{operator} WHERE id = #{id} AND deleted = 0")
    int softDeletePage(@Param("id") Long id, @Param("operator") String operator);
}

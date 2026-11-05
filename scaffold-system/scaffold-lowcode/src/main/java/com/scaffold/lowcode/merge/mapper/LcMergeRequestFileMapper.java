package com.scaffold.lowcode.merge.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 合并请求的变更文件清单。
 *
 * <p>清单随请求整批替换（先删后插）：一次重算的结论是一个整体，逐行 upsert 会留下
 * 「上一次有这次没有」的孤儿行，而那种行在页面上读起来像「这次要删掉这个文件」。
 */
@Mapper
public interface LcMergeRequestFileMapper {

    @Select("""
            SELECT id, request_id AS requestId, path, old_path AS oldPath, change_type AS changeType,
                   is_conflict AS isConflict, additions, deletions
            FROM lc_merge_request_file
            WHERE request_id = #{requestId}
            ORDER BY is_conflict DESC, path
            """)
    List<Map<String, Object>> selectByRequest(@Param("requestId") Long requestId);

    /** 列表上「几个文件 / 几个冲突」的那两个数：现算，不往请求行里存冗余计数 */
    @Select("""
            SELECT COUNT(*) total, COALESCE(SUM(is_conflict), 0) conflicts
            FROM lc_merge_request_file
            WHERE request_id = #{requestId}
            """)
    Map<String, Object> selectCounts(@Param("requestId") Long requestId);

    @Delete("DELETE FROM lc_merge_request_file WHERE request_id = #{requestId}")
    int deleteByRequest(@Param("requestId") Long requestId);

    @Insert("""
            <script>
            INSERT INTO lc_merge_request_file (request_id, path, old_path, change_type, is_conflict, additions, deletions)
            VALUES
            <foreach collection="rows" item="row" separator=",">
                (#{row.requestId}, #{row.path}, #{row.oldPath}, #{row.changeType},
                 #{row.isConflict}, #{row.additions}, #{row.deletions})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("rows") List<Map<String, Object>> rows);
}

package com.scaffold.lowcode.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.scaffold.common.exception.BusinessException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 交付项目根上的 {@code .scaffold-project.json}。
 *
 * <p>两个用途：一是让平台（和人）在仓库非空时认出「这是一个脚手架仓库」，认出来才允许初始化，
 * 避免把平台产物推进一个不相干的仓库（方案 §6.3）；二是把底座提交号写进交付物本身，
 * 与库里 {@code lc_project.base_scaffold_sha} 对齐，方便判断项目落后底座多少。
 */
public final class ProjectMarker {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private ProjectMarker() {
    }

    public static Path path(Path workspace) {
        return workspace.resolve(ScaffoldTrimPolicy.MARKER_FILE);
    }

    /**
     * 写入标记文件。
     *
     * @param baseScaffoldSha 底座 checkout 的 HEAD 提交号，读不到时传 null
     */
    public static void write(Path workspace, String projectCode, String projectName, String baseScaffoldSha,
                             String mainBranch, String genBranch) {
        Map<String, Object> marker = new LinkedHashMap<>();
        marker.put("projectCode", projectCode);
        marker.put("projectName", projectName);
        marker.put("baseScaffoldSha", baseScaffoldSha);
        marker.put("mainBranch", mainBranch);
        marker.put("generatedBranch", genBranch);
        marker.put("initializedAt", OffsetDateTime.now().toString());
        marker.put("note", "平台只写 generatedBranch 分支；本文件所在分支由使用方掌控，固定代码可自由修改。");
        try {
            Files.writeString(path(workspace), MAPPER.writeValueAsString(marker) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException("写入项目标记文件失败：" + e.getMessage());
        }
    }

    /** 工作区里是否已有标记文件（非空仓库初始化的前置条件） */
    public static boolean exists(Path workspace) {
        return Files.isRegularFile(path(workspace));
    }
}

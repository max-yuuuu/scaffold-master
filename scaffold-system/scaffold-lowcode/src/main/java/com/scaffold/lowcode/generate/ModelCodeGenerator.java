package com.scaffold.lowcode.generate;

import com.scaffold.common.exception.BusinessException;
import com.scaffold.lowcode.design.PageTarget;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 一个模型 → 一批生成产物（方案 §4.1、§4.3）。
 *
 * <p>这是生成链路上唯一的出口：模板在这里逐个渲染，落点与档位由 {@link GeneratedPaths} 定，
 * 谁要用产物就吃这份 {@link GeneratedFile} 列表 —— zip 下载给人手工取产物，
 * 工作区落盘给发布链路提交推送（方案 §4.4「同一份 Map，两条 sink」的公共出参）。
 *
 * <p>顺序是固定的：后端、前端、SQL，同一份模型两次生成的结果逐字节一致，发布 diff 才可读。
 */
@Component
public class ModelCodeGenerator {

    private final TemplateRenderer renderer;

    public ModelCodeGenerator(TemplateRenderer renderer) {
        this.renderer = renderer;
    }

    /**
     * @param model  {@code lc_model} 的一行，或任何能给出同名字段的来源（老代码生成页用的是 {@code gen_table}）
     * @param fields 该模型的字段，按 {@code sort_order} 排好
     */
    public List<GeneratedFile> renderFiles(Map<String, Object> model, List<Map<String, Object>> fields) {
        return renderFiles(model, fields, null);
    }

    /**
     * 带页面设计的生成（方案 §1.5 ②「页面是形、模型是料」）。
     *
     * @param target 发布页面那条链路给的合流入口；{@code null} 就是 zip 下载与老代码生成页，
     *               页面形状回到模型上的 {@code is_list} / {@code is_query} / {@code is_insert} 三个勾选
     */
    public List<GeneratedFile> renderFiles(Map<String, Object> model, List<Map<String, Object>> fields, PageTarget target) {
        Map<String, Object> root = GenerationInput.of(model, fields, target);
        rejectUnsupportedSwitches(root);

        String module = string(root.get("module"));
        String className = string(root.get("className"));
        String resourceSlug = string(root.get("resourceSlug"));
        String tableName = string(root.get("tableName"));

        List<GeneratedFile> files = new ArrayList<>();
        files.add(render("backend/Entity.java.ftl", GeneratedPaths.entity(module, className), root));
        files.add(render("backend/WriteRequest.java.ftl", GeneratedPaths.writeRequest(module, className), root));
        files.add(render("backend/Row.java.ftl", GeneratedPaths.row(module, className), root));
        files.add(render("backend/Mapper.java.ftl", GeneratedPaths.mapper(module, className), root));
        files.add(render("backend/Service.java.ftl", GeneratedPaths.service(module, className), root));
        // C 档紧跟着 B 档 Service：Service 的构造器就吃这个扩展类，两个文件是一对
        files.add(render("backend/Extension.java.ftl", GeneratedPaths.extension(module, className), root));
        files.add(render("backend/Controller.java.ftl", GeneratedPaths.controller(module, className), root));
        files.add(render("frontend/Api.ts.ftl", GeneratedPaths.apiTs(module, resourceSlug), root));
        files.add(render("frontend/ListView.vue.ftl", GeneratedPaths.listView(module, className), root));
        files.add(render("sql/Table.sql.ftl", GeneratedPaths.tableSql(tableName), root));
        files.add(render("sql/Menu.sql.ftl", GeneratedPaths.menuSql(tableName), root));
        return List.copyOf(files);
    }

    /**
     * 模型上勾了生成器还没实现的能力，发布直接拦掉。
     *
     * <p>为什么不「先忽略、把能生成的生成出来」：那两个开关是人在设计器里明确表达的需求，
     * 静默忽略等于交付一个「看着配置对了、实际没有」的项目，比报错更难发现（方案 §4.5 的边界要让人看得见）。
     */
    private void rejectUnsupportedSwitches(Map<String, Object> root) {
        String modelName = string(root.get("modelName"));
        if (Boolean.TRUE.equals(root.get("tree"))) {
            throw new BusinessException("模型「" + modelName + "」勾了树表，本期生成器不产出 /tree 接口与树表页面。"
                    + "树形的取数与排序请写到 C 档扩展点里手写，或先取消勾选再发布");
        }
        if (Boolean.TRUE.equals(root.get("dataScope"))) {
            throw new BusinessException("模型「" + modelName + "」勾了数据权限，本期生成器不产出 @DataScope 与部门过滤。"
                    + "过滤规则请写到 C 档扩展点里手写，或先取消勾选再发布");
        }
    }

    private GeneratedFile render(String template, String path, Map<String, Object> root) {
        return GeneratedFile.of(path, renderer.render(template, root));
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}

package com.scaffold.lowcode.generate;

import com.scaffold.common.exception.BusinessException;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * FreeMarker 渲染入口（方案 §4.1）。模板随本模块 resources 发布，交付项目里没有这个模块，
 * 所以模板只在平台侧存在 —— 生成项目拿到的只有渲染结果。
 */
@Component
public class TemplateRenderer {

    /** 模板根目录：classpath 下的 {@code templates/lowcode} */
    private static final String TEMPLATE_BASE = "templates/lowcode";

    private final Configuration configuration;

    public TemplateRenderer() {
        configuration = new Configuration(Configuration.VERSION_2_3_32);
        // MyBatis 的参数占位写成 #{x}，而 FreeMarker 兼容模式下同样认 #{...} 是插值 ——
        // 不显式锁定 dollar 语法，每一段生成的 SQL 都会被模板引擎当成自己的表达式解析掉。
        configuration.setInterpolationSyntax(Configuration.DOLLAR_INTERPOLATION_SYNTAX);
        configuration.setClassLoaderForTemplateLoading(TemplateRenderer.class.getClassLoader(), TEMPLATE_BASE);
        configuration.setDefaultEncoding(StandardCharsets.UTF_8.name());
        configuration.setNumberFormat("computer");
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);
        configuration.setWrapUncheckedExceptions(true);
    }

    /**
     * @param template 相对 {@code templates/lowcode} 的模板路径，如 {@code backend/Entity.java.ftl}
     * @param model    {@link GenerationInput} 装配出来的数据模型
     */
    public String render(String template, Map<String, Object> model) {
        try {
            Template loaded = configuration.getTemplate(template);
            StringWriter out = new StringWriter();
            loaded.process(model, out);
            return out.toString();
        } catch (IOException | TemplateException e) {
            throw new BusinessException("模板渲染失败：" + template + "（" + e.getMessage() + "）");
        }
    }
}

package com.scaffold.business.${module}.service;

import com.scaffold.business.${module}.domain.${className};
import com.scaffold.business.${module}.domain.${className}Row;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ${modelDesc}的人工扩展点（表 {@code ${tableName}}）。
 *
 * <p>本类属 <b>C 档</b>：只在模型首次发布时生成这一份空骨架，之后平台永不覆盖。
 * 判据很简单 —— 靠字段名和类型推导不出来的东西才写到这里：状态流转的规则、跨实体的事务、
 * 调外部系统、聚合计算。能配置出来的请回到模型里改，别在生成物外面补一份。
 *
 * <p><b>方法签名是平台契约。</b>下面这些钩子由 B 档 {@code ${className}Service} 逐个调用，
 * 改名或删方法会在下一次发布时直接编译失败 —— 这是刻意选的：可见失败优于静默改行为。
 * 要加自己的方法请随便加，平台看不见也不碰。
 *
 * <p>钩子都在 Service 的 {@code @Transactional} 里跑。{@code before*} 抛异常就挡住这次写入；
 * {@code after*} 抛异常会连带回滚，所以「先落库再通知外部系统」这类需求要自己处理补偿，
 * 别指望 {@code after*} 是提交之后才执行的。
 *
 * <p>需要读库就在构造器里注入 {@code ${className}Mapper}，需要动别的业务就注入对应的 Service。
 */
@Component
public class ${className}Extension {

    /**
     * 列表与导出的行取完之后。传进来的就是页面与 CSV 将要展示的那批对象，改内容即改读数 ——
     * 补聚合值、把 code 换成中文都在这里做。
     */
    public void afterRows(List<${className}Row> rows) {
        // TODO 人工填写：平台只在首次发布生成本类，之后不再覆盖
    }

    /** 入库前：业务校验、补默认值。抛出 {@code BusinessException} 就会挡住新增 */
    public void beforeCreate(${className} entity) {
        // TODO 人工填写
    }

    /** 入库后：主键已回填，联动写别的表、发通知 */
    public void afterCreate(${className} entity) {
        // TODO 人工填写
    }

    /** 更新前：这一行的新值（只含勾选了「可编辑」的字段），旧值请自己按 {@code id} 查 */
    public void beforeUpdate(Long id, ${className} entity) {
        // TODO 人工填写
    }

    /** 更新后 */
    public void afterUpdate(Long id, ${className} entity) {
        // TODO 人工填写
    }

    /** 删除前：勾了「逻辑删除」的表翻标记，没勾的是真删 —— 需要拦一道就在这个方法里抛 */
    public void beforeDelete(Long id) {
        // TODO 人工填写
    }

    /** 删除后：清理关联数据 */
    public void afterDelete(Long id) {
        // TODO 人工填写
    }
}

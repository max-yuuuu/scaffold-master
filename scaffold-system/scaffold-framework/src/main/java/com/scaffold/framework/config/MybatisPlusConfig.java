package com.scaffold.framework.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.scaffold.framework.tenant.ScaffoldTenantLineHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件注册。当前只挂多租户条件注入；后续加分页等内部插件时，
 * TenantLineInnerInterceptor 必须排在最前，否则分页 count 统计不到租户条件过滤后的行数。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new ScaffoldTenantLineHandler()));
        return interceptor;
    }
}

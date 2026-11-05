package com.scaffold.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

@MapperScan({
        "com.scaffold.system.mapper",
        // scaffold:platform-only:start 平台侧模块的 Mapper 包。低代码模块必须逐个列到 *.mapper，
        // 不能写包根 com.scaffold.lowcode —— @MapperScan 会把包树里所有接口都当 Mapper 注册，
        // GitGateway 这种服务接口就会多出一个 bean 把注入打成二义
        "com.scaffold.generator.mapper",
        "com.scaffold.lowcode.tenant.mapper",
        "com.scaffold.lowcode.project.mapper",
        "com.scaffold.lowcode.model.mapper",
        "com.scaffold.lowcode.page.mapper",
        "com.scaffold.lowcode.publish.mapper",
        "com.scaffold.lowcode.merge.mapper",
        // scaffold:platform-only:end
        // 业务模块按包根扫：生成代码会往 com.scaffold.business.{模型} 下不断加子包，pom 与本行都不该再改。
        // 代价是这个包里的接口一律按 Mapper 注册，所以业务侧的接口（客户端、SPI 等）要写成类或放别的包
        "com.scaffold.business"
})
@SpringBootApplication(scanBasePackages = "com.scaffold")
public class ScaffoldAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScaffoldAdminApplication.class, args);
    }
}

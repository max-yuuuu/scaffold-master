package com.scaffold.lowcode.tenant.controller;

import com.scaffold.common.domain.R;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.framework.audit.OperLog;
import com.scaffold.framework.security.Anonymous;
import com.scaffold.framework.security.CaptchaService;
import com.scaffold.lowcode.tenant.domain.TenantRegisterRequest;
import com.scaffold.lowcode.tenant.service.TenantRegistrationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 租户自助注册（匿名接口，与登录页共用一套版式）。验证码与登录同规则：
 * 开关关闭时直接放行（本地开发与集成测试），开启时必须通过。
 */
@RestController
@RequestMapping("/api/auth")
public class TenantRegisterController {

    private final TenantRegistrationService registrationService;
    private final CaptchaService captchaService;

    public TenantRegisterController(TenantRegistrationService registrationService, CaptchaService captchaService) {
        this.registrationService = registrationService;
        this.captchaService = captchaService;
    }

    @Anonymous
    @PostMapping("/register")
    @OperLog(module = "租户注册", action = "注册开通")
    public R<Map<String, Object>> register(@Valid @RequestBody TenantRegisterRequest request) {
        if (!captchaService.verify(request.getCaptchaUuid(), request.getCaptcha())) {
            throw new BusinessException("验证码错误或已过期");
        }
        return R.success(registrationService.register(request));
    }
}

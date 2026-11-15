package com.coffee.config;

import com.coffee.security.AdminTokenInterceptor;
import com.coffee.security.LoginInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web MVC 配置：注册小程序登录拦截器、后台 admin-token 拦截器与上传目录映射
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;
    private final AdminTokenInterceptor adminTokenInterceptor;
    private final UploadProperties uploadProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 小程序顾客接口：只接受 scope = CUSTOMER 的 Token，后台接口完全排除
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/**",
                        "/api/menu/**",
                        "/api/activity/**",
                        "/api/pay/notify/**",
                        "/api/health",
                        "/api/admin/**");

        // 店长后台接口：只接受 admin-token，登录接口本身无需鉴权
        registry.addInterceptor(adminTokenInterceptor)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/auth/login");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadRoot = Paths.get(uploadProperties.dir()).toAbsolutePath().normalize();
        String location = uploadRoot.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler(uploadProperties.publicPrefix() + "**")
                .addResourceLocations(location);
    }
}

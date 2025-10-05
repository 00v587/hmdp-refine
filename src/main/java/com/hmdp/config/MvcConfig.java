package com.hmdp.config;

import com.hmdp.properties.JwtProperties;
import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * @author zz
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private JwtProperties jwtProperties;

    // 配置拦截器
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate, jwtProperties))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/",
                        "/index.html",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/user/code",
                        "/user/login",
                        "/upload/**",
                        "/error",
                        
                        // JMeter测试接口
                        "/test/**"           // 测试接口，用于生成token
                )
                .order(0);

        registry.addInterceptor(new LoginInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(
                        // 公开的静态资源
                        "/",
                        "/index.html",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/upload/**",
                        "/error",

                        // 公开的API接口
                        "/user/code",        // 登录验证码
                        "/user/login",       // 登录
                        "/shop/**",          // 所有商铺接口（游客可访问）
                        "/api/shop/**",      // 所有商铺接口
                        "/voucher/list/**",       // 代金券相关
                        "/api/blog/hot",     // 热门博客
                        "/blog/hot",         // 热门博客
                        "/shop-type/list",   // 店铺类型列表
                        
                        // JMeter测试接口
                        "/test/**"           // 测试接口，用于生成token
                )
                .order(1);
    }
}
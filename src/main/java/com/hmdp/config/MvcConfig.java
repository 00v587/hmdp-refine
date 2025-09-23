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
        //拦截所有请求，刷新token有效期
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate, jwtProperties))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/",
                        "/index.html",
                        "/css/**",
                        "/js/**",
                        "/images/**"
                        // 静态资源
                )
                .order(0);

        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/",
                        "/index.html",
                        "/user/code", // 登录验证码
                        "/user/login", // 登录
                        "/shop/**", // 店铺信息
                        "/voucher/**", // 代金券相关
                        "/upload/**", // 文件上传
                        "/api/blog/hot",// 热门博客
                        "/shop-type/list",
                        "/blog/hot"// 热门博客

                )
                .order(1);
    }
}

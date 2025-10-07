package com.hmdp.utils.interceptor;

import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 从 ThreadLocal 中获取用户
        UserDTO user = UserHolder.getUser();
        log.info("LoginInterceptor: 检查请求路径 -> {}", request.getRequestURI());
        
        // 特殊处理error路径，总是放行
        if ("/error".equals(request.getRequestURI())) {
            log.info("LoginInterceptor: error路径，直接放行");
            return true;
        }
        
        // 2. 判断用户是否存在
        if (user == null) {
            log.info("LoginInterceptor: 用户未登录，拦截 -> {}", request.getRequestURI());
            response.setStatus(401);
            return false;
        }
        // 3. 用户存在，放行
        log.info("LoginInterceptor: 用户已登录 -> {}", user);
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    // 移除用户
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
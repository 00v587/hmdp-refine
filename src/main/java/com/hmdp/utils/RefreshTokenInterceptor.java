package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.properties.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

@Slf4j
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;
    // 直接通过构造器注入
    private final JwtProperties jwtProperties;


    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate, JwtProperties jwtProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        // 手动接收依赖
        this.jwtProperties = jwtProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        log.info("=== RefreshTokenInterceptor 开始执行 ===");
        log.info("当前请求URL: {}", request.getRequestURI());

        // 1. 统一从 Authorization 里取
        String authHeader = request.getHeader("Authorization");
        log.info("Authorization raw: {}", authHeader);

        if (StrUtil.isBlank(authHeader)) {
            log.info("Authorization 为空，直接放行");
            return true;
        }

        // 2. 去掉 Bearer 前缀
        String token = authHeader.trim();
        if (StrUtil.startWithIgnoreCase(token, "Bearer ")) {
            token = token.substring(7).trim();
        }
        log.info("最终处理后的 token: {}", token);

        if (StrUtil.isBlank(token)) {
            log.info("token 为空，直接放行");
            return true;
        }

        try {
            // 3. 解析 JWT
            log.info("开始解析 token...");
            Claims claims = JwtUtil.parseJWT(token,jwtProperties.getUserSecretKey());
            Long userId = claims.get(JwtClaimsConstant.USER_ID, Long.class);
            log.info("解析成功，userId: {}", userId);

            // 4. 基于 userId 查询 Redis
            String key = LOGIN_USER_KEY + userId;
            Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
            log.info("Redis中用户信息: {}", userMap);

            if (userMap.isEmpty()) {
                log.info("Redis 中用户不存在，直接放行");
                return true;
            }

            // 5. 校验 token 一致性
            String redisToken = (String) userMap.get("jwttoken");
            log.info("Redis 中的 token: {}", redisToken);

            if (!token.equals(redisToken)) {
                log.info("token 不一致，直接放行");
                return true;
            }

            // 6. 转 DTO 存入 ThreadLocal
            UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
            log.info("转换为 UserDTO 成功: {}", userDTO);
            UserHolder.saveUser(userDTO);

            // 7. 刷新 token 有效期
            stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.SECONDS);
            log.info("token 有效期已刷新");

            return true;
        } catch (ExpiredJwtException e) {
            log.warn("token 已过期: {}", e.getMessage());
            return true;
        } catch (Exception e) {
            log.error("token 解析失败", e);
            return true;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除用户
        UserHolder.removeUser();
    }
}
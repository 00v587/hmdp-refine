package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.properties.JwtProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * Token生成器 - 用于JMeter性能测试
 * 批量生成用户token并保存到文件
 */
@Slf4j
@Component
public class TokenGenerator {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 为指定数量的用户生成token
     * @param userCount 用户数量
     * @param outputFile 输出文件路径
     */
    public void generateTokensForUsers(int userCount, String outputFile) {
        try {
            // 查询指定数量的用户
            List<User> users = userMapper.selectList(null);
            if (users.size() < userCount) {
                log.warn("数据库中的用户数量({})少于请求的数量({})", users.size(), userCount);
                userCount = users.size();
            }

            // 创建输出文件
            BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
            
            // 写入CSV头部
            writer.write("user_id,phone,nick_name,token");
            writer.newLine();

            int generatedCount = 0;
            
            for (int i = 0; i < Math.min(userCount, users.size()); i++) {
                User user = users.get(i);
                
                try {
                    // 生成JWT token
                    Map<String, Object> claims = new HashMap<>();
                    claims.put(JwtClaimsConstant.USER_ID, user.getId());
                    String jwtToken = JwtUtil.createJWT(
                            jwtProperties.getUserSecretKey(),
                            jwtProperties.getUserTtl(),
                            claims
                    );

                    // 创建UserDTO
                    UserDTO userDTO = new UserDTO();
                    BeanUtil.copyProperties(user, userDTO);

                    // 转换为Map并存储到Redis
                    Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                            CopyOptions.create()
                                    .setIgnoreNullValue(true)
                                    .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

                    String tokenKey = LOGIN_USER_KEY + userDTO.getId();
                    userMap.put("jwttoken", jwtToken);
                    redisTemplate.opsForHash().putAll(tokenKey, userMap);
                    redisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.SECONDS);

                    // 写入文件
                    writer.write(String.format("%d,%s,%s,%s",
                            user.getId(),
                            user.getPhone(),
                            user.getNickName(),
                            jwtToken));
                    writer.newLine();

                    generatedCount++;
                    
                    if (generatedCount % 100 == 0) {
                        log.info("已生成 {} 个token", generatedCount);
                    }
                    
                } catch (Exception e) {
                    log.error("为用户 {} 生成token失败: {}", user.getId(), e.getMessage());
                }
            }

            writer.close();
            log.info("Token生成完成！共生成 {} 个token，保存到文件: {}", generatedCount, outputFile);
            
        } catch (IOException e) {
            log.error("文件写入失败: {}", e.getMessage());
        }
    }

    /**
     * 为所有用户生成token
     * @param outputFile 输出文件路径
     */
    public void generateTokensForAllUsers(String outputFile) {
        List<User> users = userMapper.selectList(null);
        generateTokensForUsers(users.size(), outputFile);
    }
}
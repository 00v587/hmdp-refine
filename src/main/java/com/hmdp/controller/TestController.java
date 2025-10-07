package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.utils.generator.TokenGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 测试控制器 - 用于JMeter性能测试准备
 */
@Slf4j
@RestController
@RequestMapping("/test")
public class TestController {

    @Autowired
    private TokenGenerator tokenGenerator;

    /**
     * 为指定数量的用户生成token
     * @param count 用户数量
     * @param filename 输出文件名
     * @return 生成结果
     */
    @PostMapping("/generate-tokens")
    public Result generateTokens(@RequestParam(defaultValue = "1000") int count,
                                @RequestParam(defaultValue = "tokens.csv") String filename) {
        try {
            tokenGenerator.generateTokensForUsers(count, filename);
            return Result.ok("Token生成完成，文件: " + filename);
        } catch (Exception e) {
            log.error("生成token失败: {}", e.getMessage());
            return Result.fail("生成token失败: " + e.getMessage());
        }
    }

    /**
     * 为所有用户生成token
     * @param filename 输出文件名
     * @return 生成结果
     */
    @PostMapping("/generate-all-tokens")
    public Result generateAllTokens(@RequestParam(defaultValue = "all_tokens.csv") String filename) {
        try {
            tokenGenerator.generateTokensForAllUsers(filename);
            return Result.ok("所有用户token生成完成，文件: " + filename);
        } catch (Exception e) {
            log.error("生成所有用户token失败: {}", e.getMessage());
            return Result.fail("生成所有用户token失败: " + e.getMessage());
        }
    }

    /**
     * 获取当前用户数量
     * @return 用户数量
     */
    @GetMapping("/user-count")
    public Result getUserCount() {
        try {
            // 这里需要注入UserMapper，暂时返回简单信息
            return Result.ok("请调用generate-tokens接口查看实际用户数量");
        } catch (Exception e) {
            return Result.fail("获取用户数量失败: " + e.getMessage());
        }
    }
}
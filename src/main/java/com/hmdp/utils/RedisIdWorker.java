package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    /**
     * 开始时间戳
     * @param keyPrefix
     * @return
     */
    private static final long BEGIN_TIMESTAMP = 1758758400L;

    /**
     * 序列号位数
     */
    private static final long COUNT_BITS = 32;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    public long nextId(String keyPrefix) {
        // 1. 生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;
        // 2. 生成序列号 自增
        // 2.1 拼接当天日期做key
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long serialNumber = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        // 3. 拼接并返回
        return timestamp << COUNT_BITS | serialNumber;
    }
}

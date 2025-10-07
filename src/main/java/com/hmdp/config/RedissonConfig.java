package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SentinelServersConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    @Primary  // 确保这个bean优先使用
    public RedissonClient redisson() {
        Config config = new Config();

        config.useSentinelServers()
                .setMasterName("mymaster")
                .setCheckSentinelsList(false)  // 关键：禁用哨兵列表检查
                .addSentinelAddress("redis://127.0.0.1:26379")
                .addSentinelAddress("redis://127.0.0.1:26380")
                .addSentinelAddress("redis://127.0.0.1:26381");

        return Redisson.create(config);
    }
}

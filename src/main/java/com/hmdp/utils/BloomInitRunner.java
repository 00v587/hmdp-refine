package com.hmdp.utils;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class BloomInitRunner implements ApplicationRunner {

    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private IShopService shopService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        RBloomFilter<Long> shopBloom = redissonClient.getBloomFilter("shop:bloom");
        // 初始化：预估元素数量+容错率
        // 100w 数据，1%误判率
        shopBloom.tryInit(1000000L, 0.01);

        // 把数据库已有的shopId都加入布隆过滤器
        List<Long> ids = shopService.list().stream().map(Shop::getId).collect(Collectors.toList());
        ids.forEach(shopBloom::add);
    }
}
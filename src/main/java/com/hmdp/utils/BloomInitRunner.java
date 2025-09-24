package com.hmdp.utils;

import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.mapper.VoucherMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class BloomInitRunner implements ApplicationRunner {

    // 实现了 ApplicationRunner 接口 在应用启动时自动执行初始化操作

    @Autowired
    private RedissonClient redissonClient;

    // 店铺布隆过滤器
    private RBloomFilter<Long> shopFilter;
    // 用户布隆过滤器
    private RBloomFilter<Long> userFilter;
    // 优惠券ID布隆过滤器
    private RBloomFilter<Long> voucherFilter;
    // 秒杀券布隆过滤器
    private RBloomFilter<Long> seckillFilter;

    @Autowired
    private ShopMapper shopMapper;
    @Autowired
    private VoucherMapper voucherMapper;
    @Autowired
    private SeckillVoucherMapper seckillVoucherMapper;
    @Autowired
    private UserMapper userMapper;

    private void initShopBloomFilter() {
         shopFilter = redissonClient.getBloomFilter("shop:bloom");
         shopFilter.tryInit(50000L, 0.01); // 5万容量，1%误判率

        // 加载所有有效店铺ID
        List<Long> shopIds = shopMapper.selectAllIds();
        shopIds.forEach(shopFilter::add);

//        // 验证布隆过滤器是否正常工作
//        if (!shopIds.isEmpty()) {
//            log.info("测试包含第一个店铺ID: " + shopFilter.contains(shopIds.get(13)));
//        }
    }

    private void initUserBloomFilter() {
        userFilter = redissonClient.getBloomFilter("user:bloom");
        userFilter.tryInit(50000L, 0.01); // 5万容量，1%误判率

        // 加载所有有效店铺ID
        List<Long> userIds = userMapper.selectAllIds();
        userIds.forEach(userFilter::add);
    }

    private void initVoucherBloomFilter() {
        voucherFilter = redissonClient.getBloomFilter("voucher:bloom");
        voucherFilter.tryInit(50000L, 0.01); // 5万容量，1%误判率

        // 加载所有有效店铺ID
        List<Long> voucherIds = voucherMapper.selectAllIds();
        voucherIds.forEach(voucherFilter::add);
    }

    private void initSeckillBloomFilter() {
        seckillFilter = redissonClient.getBloomFilter("seckill:bloom");
        seckillFilter.tryInit(50000L, 0.01); // 5万容量，1%误判率

        // 加载所有有效店铺ID
        List<Long> seckillIds = seckillVoucherMapper.selectAllIds();
        seckillIds.forEach(seckillFilter::add);
    }

    // 店铺相关方法
    public boolean mightContainShop(Long shopId) {
        return shopFilter.contains(shopId);
    }

    public void addShop(Long shopId) {
        shopFilter.add(shopId);
    }

    // 用户相关方法
    public boolean mightContainUser(Long userId) {
        return userFilter.contains(userId);
    }

    public void addUser(Long userId) {
        userFilter.add(userId);
    }

    // 商品相关方法
    public boolean mightContainVoucher(Long voucherId) {
        return voucherFilter.contains(voucherId);
    }

    public void addseckill(Long seckillId) {
        seckillFilter.add(seckillId);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        initShopBloomFilter();
        initUserBloomFilter();
        initVoucherBloomFilter();
        initSeckillBloomFilter();
    }
}
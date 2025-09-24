package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.CacheStorageStrategy;
import com.hmdp.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private ShopMapper shopMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    @Resource
    private RedissonClient redissonClient;

    private final CacheClient cacheClient;

    public ShopServiceImpl(@Lazy CacheClient cacheClient) {
        this.cacheClient = cacheClient;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    /**
     * 根据id查询商铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @Override
    public Result queryById(Long id){

        //布隆过滤器 解决缓存穿透
        RBloomFilter<Long> shopBloom = redissonClient.getBloomFilter("shop:bloom");
        // 如果布隆过滤器里不存在，直接返回不存在，避免打到DB
        if (!shopBloom.contains(id)) {
            return Result.fail("店铺不存在");
        }

        //缓存穿透+缓存雪崩
//        Shop shop = cacheClient
//                .queryByIdWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES, CacheStorageStrategy.HASH);

        //互斥锁解决缓存击穿
        //Shop shop = queryByIdWithMutex(id);

        //逻辑过期解决缓存击穿
        Shop shop = cacheClient
                .queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES, CacheStorageStrategy.HASH);

        if (shop == null){
            return Result.fail("店铺不存在");
        }

        return Result.ok(shop);
    }



    /**
     * 缓存击穿 互斥锁
     * @param id
     * @return
     */
    //代码中使用了while(true)循环尝试获取锁，但没有设置最大重试次数。
//    private Shop queryByIdWithMutex(Long id) {
//        //1. 查找redis
//        String key = CACHE_SHOP_KEY + id;
//
//        // 缓存存在，返回缓存数据
//        Map<Object, Object> shopMap = stringRedisTemplate.opsForHash().entries(key);
//        if (!shopMap.isEmpty()) {
//            if (shopMap.containsKey("")) {
//                // 空值缓存，说明数据库中也无此数据
//                return null;
//            }
//            // 反序列化hash为对象
//            Shop shop = BeanUtil.mapToBean(shopMap, Shop.class, true, CopyOptions.create());
//            return shop;
//        }
//
//        //3. 缓存不存在 获取锁
//        String lock = LOCK_SHOP_KEY + id;
//        Shop shop = null;
//        try {
//            while (true) {
//                // 使用循环替代递归
//                boolean isLock = tryLock(lock);
//                if (isLock) {
//                    // 4. 获取锁成功，查询数据库
//                    shop = shopMapper.selectById(id);
//
//                    // 模拟重建延时，防止缓存击穿时大量请求同时访问数据库
//                    Thread.sleep(200);
//
//                    // 5. 数据库有，写入redis
//                    if (shop == null) {
//                        // 将空值缓存一段时间，防止缓存穿透
//                        stringRedisTemplate.opsForHash().put(key, "", "");
//                        stringRedisTemplate.expire(key, CACHE_NULL_TTL, TimeUnit.MINUTES);
//                    } else {
//                        // 6. 数据库有，写入redis
//                        Map<String, Object> shopMapForRedis = BeanUtil.beanToMap(shop, new HashMap<>(),
//                                CopyOptions.create()
//                                        .setIgnoreNullValue(true)
//                                        .setFieldValueEditor((fieldName, fieldValue) ->
//                                                fieldValue != null ? fieldValue.toString() : null));
//                        stringRedisTemplate.opsForHash().putAll(key, shopMapForRedis);
//                        stringRedisTemplate.expire(key, CACHE_SHOP_TTL, TimeUnit.MINUTES);
//                    }
//                    break; // 获取锁并处理完成后跳出循环
//                } else {
//                    // 4. 获取锁失败，休眠后重试
//                    Thread.sleep(50);
//                    // 循环继续，再次尝试获取锁
//                }
//            }
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            //6. 释放锁
//            unLock(lock);
//        }
//        return shop;
//    }

    /**
     * 缓存null防止缓存穿透和缓存雪崩
     */
    //    public Shop queryByIdWithPassThrough(Long id) {
    //        //TODO 热点限流
    //
    //        // 1. 查询redis
    //        String key = CACHE_SHOP_KEY + id;
    //        Map<Object, Object> shopMap = stringRedisTemplate.opsForHash().entries(key);
    //
    //        // 2. redis存在，转换为shop对象
    //        if (!shopMap.isEmpty()) {
    //            try {
    //                // 转换为shop对象
    //                // 先将所有值转换为String，再使用BeanUtil转换
    //                Map<String, Object> convertedMap = new HashMap<>();
    //                for (Map.Entry<Object, Object> entry : shopMap.entrySet()) {
    //                    String field = entry.getKey().toString();
    //                    Object value = entry.getValue();
    //                    convertedMap.put(field, value != null ? value.toString() : null);
    //                }
    //
    //                Shop shop = BeanUtil.mapToBean(convertedMap, Shop.class, true, new CopyOptions()
    //                        .ignoreNullValue()
    //                        .setFieldValueEditor((fieldName, fieldValue) -> {
    //                            // 处理数字类型的转换
    //                            if (fieldValue == null) {
    //                                return null;
    //                            }
    //                            String strValue = fieldValue.toString();
    //                            if (strValue.isEmpty()) {
    //                                return null;
    //                            }
    //
    //                            // 根据字段类型进行转换
    //                            try {
    //                                if ("id".equals(fieldName) || "typeId".equals(fieldName)
    //                                        || "sold".equals(fieldName) || "comments".equals(fieldName)
    //                                        || "score".equals(fieldName)) {
    //                                    return Long.parseLong(strValue);
    //                                } else if ("avgPrice".equals(fieldName)) {
    //                                    return Integer.parseInt(strValue);
    //                                } else if ("x".equals(fieldName) || "y".equals(fieldName)) {
    //                                    return Double.parseDouble(strValue);
    //                                }
    //                            } catch (NumberFormatException e) {
    //                                log.error("字段转换错误: {} = {}", fieldName, strValue);
    //                                return null;
    //                            }
    //                            return strValue;
    //                        }));
    //
    //                return shop;
    //
    //            } catch (Exception e) {
    //                log.error("Redis数据转换失败，从数据库查询", e);
    //                // 如果转换失败，从数据库查询
    //            }
    //        }
    //
    //        // 缓存空对象的情况，直接返回null
    //        if (shopMap != null && shopMap.containsKey("")) {
    //            return null;
    //        }
    //
    //        // 3. redis没有，查询数据库
    //        Shop shop = shopMapper.selectById(id);
    //        if (shop == null) {
    //            // 将空值缓存一段时间，防止缓存穿透
    //            stringRedisTemplate.opsForHash().put(key, "", "");
    //            stringRedisTemplate.expire(key, CACHE_NULL_TTL, TimeUnit.MINUTES);
    //            return null;
    //        }
    //
    //        // 4. 数据库有，写入redis
    //        Map<String, Object> shopMapForRedis = BeanUtil.beanToMap(shop, new HashMap<>(),
    //                CopyOptions.create()
    //                        .setIgnoreNullValue(true)
    //                        .setFieldValueEditor((fieldName, fieldValue) ->
    //                                fieldValue != null ? fieldValue.toString() : null));
    //
    //        try {
    //            stringRedisTemplate.opsForHash().putAll(key, shopMapForRedis);
    //            // 使用随机TTL防止缓存雪崩，原始TTL为30分钟，现在在30-60分钟之间随机
    //            long randomTtl = CACHE_SHOP_TTL + new java.util.Random().nextInt(30);
    //            stringRedisTemplate.expire(key, randomTtl, TimeUnit.MINUTES);
    //        } catch (Exception e) {
    //            log.error("写入Redis失败", e);
    //        }
    //        return shop;
    //    }


    /**
     * 新增商铺信息
     * @param shop 商铺数据
     * @return 商铺id
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        //1. 更新数据时先更新数据库
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("商铺id不能为空");
        }

        int updateResult = shopMapper.updateById(shop);
        if (updateResult == 0) {
            return Result.fail("商铺不存在");
        }

        //2. 删除缓存
        String key = CACHE_SHOP_KEY + id;
        stringRedisTemplate.delete(key);

        //3. 返回结果
        return Result.ok();
    }


    /**
     * 缓存预热
     */
    @Override
    public void saveShop2Redis(Long id, long expireSeconds) {
        // 1. 查询店铺数据
        Shop shop = getById(id);
        if (shop == null) {
            return;
        }

        // 2. 按照CacheClient期望的格式创建数据结构
        Map<String, String> cacheData = new HashMap<>();
        cacheData.put("expireTime", LocalDateTime.now().plusSeconds(expireSeconds).format(DatePattern.NORM_DATETIME_FORMATTER));

        // 3. 将shop对象转换为JSON字符串并放入data字段
        String shopJson = JSONUtil.toJsonStr(shop);
        cacheData.put("data", shopJson);

        // 4. 保存到Redis
        String key = CACHE_SHOP_KEY + id;
        stringRedisTemplate.delete(key);
        stringRedisTemplate.opsForHash().putAll(key, cacheData);
        stringRedisTemplate.expire(key, CACHE_SHOP_TTL + 60, TimeUnit.MINUTES);
    }
}
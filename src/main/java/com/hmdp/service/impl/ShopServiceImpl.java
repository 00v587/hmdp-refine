package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
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
    /**
     * 根据id查询商铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @Override
    public Shop queryById(Long id){
        //TODO 热点限流

        RBloomFilter<Long> shopBloom = redissonClient.getBloomFilter("shop:bloom");
        // 如果布隆过滤器里不存在，直接返回不存在，避免打到DB
        if (!shopBloom.contains(id)) {
            return null;
        }
        
        // 1. 查询redis
        String key = CACHE_SHOP_KEY + id;
        Map<Object, Object> shopMap = stringRedisTemplate.opsForHash().entries(key);

        // 2. redis存在，转换为shop对象
        if (!shopMap.isEmpty()) {
            try {
                // 修复：先将所有值转换为String，再使用BeanUtil转换
                Map<String, Object> convertedMap = new HashMap<>();
                for (Map.Entry<Object, Object> entry : shopMap.entrySet()) {
                    String field = entry.getKey().toString();
                    Object value = entry.getValue();
                    convertedMap.put(field, value != null ? value.toString() : null);
                }

                Shop shop = BeanUtil.mapToBean(convertedMap, Shop.class, true, new CopyOptions()
                        .ignoreNullValue()
                        .setFieldValueEditor((fieldName, fieldValue) -> {
                            // 处理数字类型的转换
                            if (fieldValue == null) {
                                return null;
                            }
                            String strValue = fieldValue.toString();
                            if (strValue.isEmpty()) {
                                return null;
                            }

                            // 根据字段类型进行转换
                            try {
                                if ("id".equals(fieldName) || "typeId".equals(fieldName)
                                        || "sold".equals(fieldName) || "comments".equals(fieldName)
                                        || "score".equals(fieldName)) {
                                    return Long.parseLong(strValue);
                                } else if ("avgPrice".equals(fieldName)) {
                                    return Integer.parseInt(strValue);
                                } else if ("x".equals(fieldName) || "y".equals(fieldName)) {
                                    return Double.parseDouble(strValue);
                                }
                            } catch (NumberFormatException e) {
                                log.error("字段转换错误: {} = {}", fieldName, strValue);
                                return null;
                            }
                            return strValue;
                        }));

                // 修复：直接返回shop，避免嵌套
                return shop;

            } catch (Exception e) {
                log.error("Redis数据转换失败，从数据库查询", e);
                // 如果转换失败，从数据库查询
            }
        }
        
        // 缓存空对象的情况，直接返回null
        if (shopMap != null && shopMap.containsKey("")) {
            return null;
        }

        // 3. redis没有，查询数据库
        Shop shop = shopMapper.selectById(id);
        if (shop == null) {
            // 将空值缓存一段时间，防止缓存穿透
            stringRedisTemplate.opsForHash().put(key, "", "");
            stringRedisTemplate.expire(key, CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 4. 数据库有，写入redis（修复存储逻辑）
        Map<String, Object> shopMapForRedis = BeanUtil.beanToMap(shop, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) ->
                                fieldValue != null ? fieldValue.toString() : null));

        try {
            stringRedisTemplate.opsForHash().putAll(key, shopMapForRedis);
            // 使用随机TTL防止缓存雪崩，原始TTL为30分钟，现在在30-60分钟之间随机
            long randomTtl = CACHE_SHOP_TTL + new java.util.Random().nextInt(30);
            stringRedisTemplate.expire(key, randomTtl, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("写入Redis失败", e);
        }

        // 5. 返回数据
        return shop;
    }

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
}
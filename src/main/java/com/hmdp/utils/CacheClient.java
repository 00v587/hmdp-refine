package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.core.date.DatePattern;
import java.time.format.DateTimeFormatter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.constans.RedisConstants.LOCK_SHOP_KEY;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;
    
    // 使用静态初始化避免循环依赖
    private static final ExecutorService CACHE_REBUILD_EXECUTOR;
    
    static {
        CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10, r -> {
            Thread t = new Thread(r, "cache-rebuild-thread");
            t.setDaemon(true); // 设置为守护线程，避免阻止JVM退出
            return t;
        });
    }

    public CacheClient(@Lazy StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final DateTimeFormatter NORM_DATETIME_PATTERN = DatePattern.NORM_DATETIME_FORMATTER;
    
    /**
     * 智能设置方法
     */
    public <T> void set(String key, T value, Long time, TimeUnit unit, CacheStorageStrategy strategy) {
        switch (strategy) {
            case AUTO:
                setAuto(key, value, time, unit);
                break;
            case STRING:
                setString(key, value, time, unit);
                break;
            case HASH:
                setHash(key, value, time, unit);
                break;
        }
    }

    /**
     * 自动判断存储策略
     */
    private <T> void setAuto(String key, T value, Long time, TimeUnit unit) {
        if (value instanceof Map) {
            // Map类型使用Hash存储
            setHash(key, value, time, unit);
        } else if (isSimpleValue(value)) {
            // 简单值使用String
            setString(key, value, time, unit);
        } else {
            // 复杂对象使用String+JSON
            setString(key, value, time, unit);
        }
    }

    /**
     * Hash存储 - 适合部分更新场景
     */
    public <T> void setHash(String key, T value, Long time, TimeUnit unit) {
        Map<String, String> hashData = new HashMap<>();
        
        if (value instanceof Map) {
            hashData = convertToHashData((Map<String, Object>) value);
        } else {
            // 对象转Hash
            Map<String, Object> beanMap = BeanUtil.beanToMap(value, new HashMap<>(),
                    CopyOptions.create()
                            .setFieldValueEditor((fieldName, fieldValue) -> 
                                    fieldValue != null ? fieldValue.toString() : ""));
            for (Map.Entry<String, Object> entry : beanMap.entrySet()) {
                hashData.put(entry.getKey(), 
                        entry.getValue() != null ? entry.getValue().toString() : "");
            }
        }
        
        stringRedisTemplate.opsForHash().putAll(key, hashData);
        stringRedisTemplate.expire(key, time, unit);
    }

    /**
     * 判断是否为简单值类型
     */
    private boolean isSimpleValue(Object value) {
        return value instanceof String || 
               value instanceof Number || 
               value instanceof Boolean ||
               value instanceof Character ||
               value instanceof Enum ||
               value == null;
    }

    /**
     * 转换Map为Hash数据
     */
    private Map<String, String> convertToHashData(Map<String, Object> map) {
        Map<String, String> hashData = new HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            hashData.put(entry.getKey(), 
                    entry.getValue() != null ? entry.getValue().toString() : "");
        }
        return hashData;
    }

    /**
     * String存储 - 适合整体读写场景
     */
    public <T> void setString(String key, T value, Long time, TimeUnit unit) {
        String jsonValue = JSONUtil.toJsonStr(value);
        stringRedisTemplate.opsForValue().set(key, jsonValue, time, unit);
    }

    /**
     * 查询方法
     *
     */

    /**
     * 通用缓存穿透查询方法 - 支持Hash和String两种存储方式
     */
    public <R, ID> R queryByIdWithPassThrough(
            String keyPrefix,
            ID id,
            Class<R> type,
            Function<ID, R> dbFallback,
            Long time,
            TimeUnit unit,
            CacheStorageStrategy strategy) {  //存储策略参数

        // 1. 查询redis
        String key = keyPrefix + id;

        // 根据策略选择不同的查询逻辑
        if (strategy == CacheStorageStrategy.HASH) {
            return queryWithHashStrategy(key, id, type, dbFallback, time, unit);
        } else {
            return queryWithStringStrategy(key, id, type, dbFallback, time, unit);
        }
    }

    /**
     * Hash存储策略的查询
     */
    private <R, ID> R queryWithHashStrategy(
            String key,
            ID id,
            Class<R> type,
            Function<ID, R> dbFallback,
            Long time,
            TimeUnit unit) {

        // 1. 从Redis Hash查询
        Map<Object, Object> redisMap = stringRedisTemplate.opsForHash().entries(key);

        // 2. Redis存在，转换为对象
        if (!redisMap.isEmpty()) {
            try {
                // 检查空值标记
                if (redisMap.containsKey("_NULL_")) {
                    return null;
                }

                // 通用Hash转对象方法
                R result = convertHashToObject(redisMap, type);
                if (result != null) {
                    return result;
                }
            } catch (Exception e) {
                log.error("Redis Hash数据转换失败，key: {}, 从数据库查询", key, e);
            }
        }

        // 3. Redis没有，查询数据库
        R result = dbFallback.apply(id);

        // 4. 数据库不存在，缓存空值
        if (result == null) {
            try {
                stringRedisTemplate.opsForHash().put(key, "_NULL_", "1");
                // 使用与正常数据相同的随机TTL策略
                long baseTtl = unit.toSeconds(time);
                long randomTtl = baseTtl + new Random().nextInt((int) (baseTtl * 0.5));
                stringRedisTemplate.expire(key, randomTtl, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("缓存空值失败", e);
            }
            return null;
        }

        // 5. 数据库存在，写入Redis Hash
        try {
            Map<String, Object> mapForRedis = BeanUtil.beanToMap(result, new HashMap<>(),
                    CopyOptions.create()
                            .setIgnoreNullValue(true)
                            .setFieldValueEditor((fieldName, fieldValue) ->
                                    fieldValue != null ? fieldValue.toString() : null));

            stringRedisTemplate.opsForHash().putAll(key, mapForRedis);

            // 使用随机TTL防止缓存雪崩
            long baseTtl = unit.toSeconds(time);
            long randomTtl = baseTtl + new Random().nextInt((int) (baseTtl * 0.5)); // 增加50%的随机时间
            stringRedisTemplate.expire(key, randomTtl, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.error("写入Redis Hash失败", e);
        }

        return result;
    }

    /**
     * String存储策略的查询
     */
    private <R, ID> R queryWithStringStrategy(
            String key,
            ID id,
            Class<R> type,
            Function<ID, R> dbFallback,
            Long time,
            TimeUnit unit) {

        // 1. 从Redis String查询
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2. Redis存在，转换为对象
        if (StrUtil.isNotBlank(json)) {
            // 检查空值标记
            if (json.equals("_NULL_")) {
                return null;
            }

            try {
                R result = JSONUtil.toBean(json, type);
                if (result != null) {
                    return result;
                }
            } catch (Exception e) {
                log.error("Redis JSON数据解析失败，key: {}, 从数据库查询", key, e);
            }
        }

        // 3. Redis没有，查询数据库
        R result = dbFallback.apply(id);

        // 4. 数据库不存在，缓存空值
        if (result == null) {
            try {
                stringRedisTemplate.opsForValue().set(key, "_NULL_",
                        // 同样使用随机TTL策略，保持一致性
                        time, unit);
            } catch (Exception e) {
                log.error("缓存空值失败", e);
            }
            return null;
        }

        // 5. 数据库存在，写入Redis String
        try {
            String jsonValue = JSONUtil.toJsonStr(result);

            // 使用随机TTL防止缓存雪崩
            long baseTtl = unit.toSeconds(time);
            long randomTtl = baseTtl + new Random().nextInt((int) (baseTtl * 0.5));

            stringRedisTemplate.opsForValue().set(key, jsonValue, randomTtl, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.error("写入Redis String失败", e);
        }

        return result;
    }

    /**
     * 逻辑过期解决缓存击穿
     */
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix,
            ID id,
            Class<R> type,
            Function<ID, R> dbFallback,
            Long expireTime,
            TimeUnit unit,
            CacheStorageStrategy strategy) {

        String key = keyPrefix + id;

        // 根据策略选择不同的查询逻辑
        if (strategy == CacheStorageStrategy.HASH) {
            return queryWithLogicalExpireHashStrategy(key, id, type, dbFallback, expireTime, unit);
        } else {
            return queryWithLogicalExpireStringStrategy(key, id, type, dbFallback, expireTime, unit);
        }
    }

    private <R, ID> R queryWithLogicalExpireStringStrategy(String key, ID id, Class<R> type, Function<ID,R> dbFallback, Long expireTime, TimeUnit unit) {
        try {
            // 1. 从Redis查询缓存
            Map<Object, Object> cacheMap = stringRedisTemplate.opsForHash().entries(key);

            // 2. 缓存不存在
            if (cacheMap.isEmpty()) {
                return null;
            }

            // 3. 检查是否是空值缓存
            if (cacheMap.containsKey("_NULL_")) {
                return null;
            }

            // 4. 检查是否是逻辑过期格式
            if (cacheMap.containsKey("expireTime") && cacheMap.containsKey("data")) {
                return handleLogicalExpireFormat(cacheMap, key, id, type, dbFallback, expireTime, unit);
            } else {
                // 处理传统格式并异步转换
                return handleLegacyFormat(cacheMap, key, id, type, dbFallback, expireTime, unit);
            }
        } catch (Exception e) {
            // 处理Redis数据类型不匹配等异常
            log.warn("Redis缓存读取异常，可能由于数据类型不匹配，key: {}", key, e);
            // 清理异常的key
            stringRedisTemplate.delete(key);
            return null;
        }
    }

    private <R, ID> R queryWithLogicalExpireHashStrategy(String key, ID id, Class<R> type, Function<ID,R> dbFallback, Long expireTime, TimeUnit unit) {
        try {
            // 1. 从Redis查询缓存
            Map<Object, Object> cacheMap = stringRedisTemplate.opsForHash().entries(key);

            // 2. 缓存不存在
            if (cacheMap.isEmpty()) {
                return null;
            }

            // 3. 检查是否是空值缓存
            if (cacheMap.containsKey("_NULL_")) {
                return null;
            }

            // 4. 检查是否是逻辑过期格式
            if (cacheMap.containsKey("expireTime") && cacheMap.containsKey("data")) {
                return handleLogicalExpireFormat(cacheMap, key, id, type, dbFallback, expireTime, unit);
            } else {
                // 处理传统格式并异步转换
                return handleLegacyFormat(cacheMap, key, id, type, dbFallback, expireTime, unit);
            }
        } catch (Exception e) {
            // 处理Redis数据类型不匹配等异常
            log.warn("Redis缓存读取异常，可能由于数据类型不匹配，key: {}", key, e);
            // 清理异常的key
            stringRedisTemplate.delete(key);
            return null;
        }
    }

    private <R, ID> R handleLogicalExpireFormat(Map<Object, Object> cacheMap, String key,
                                                ID id, Class<R> type, Function<ID, R> dbFallback, Long expireTime, TimeUnit unit) {

        try {
            // 解析逻辑过期时间
            String expireTimeStr = String.valueOf(cacheMap.get("expireTime"));
            LocalDateTime expireTimeInCache = LocalDateTime.parse(expireTimeStr, NORM_DATETIME_PATTERN);

            // 判断是否逻辑过期
            if (LocalDateTime.now().isBefore(expireTimeInCache)) {
                // 未过期，直接返回数据
                return extractDataFromCache(cacheMap.get("data"), type);
            }

            // 已过期，尝试获取锁重建缓存
            String lockKey = LOCK_SHOP_KEY + id;
            if (tryLock(lockKey)) {
                // 获取锁成功，异步重建缓存
                CACHE_REBUILD_EXECUTOR.submit(() -> rebuildCache(key, id, dbFallback, expireTime, unit, lockKey));
            }

            // 返回过期的数据
            return extractDataFromCache(cacheMap.get("data"), type);

        } catch (Exception e) {
            log.error("处理逻辑过期格式缓存失败", e);
            return null;
        }
    }


    private <R> R extractDataFromCache(Object dataObj, Class<R> type) {
        if (dataObj instanceof Map) {
            Map<?, ?> rawMap = (Map<?, ?>) dataObj;
            Map<Object, Object> dataMap = new HashMap<>();

            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                dataMap.put(String.valueOf(entry.getKey()), entry.getValue());
            }

            return convertHashToObject(dataMap, type);
        } else if (dataObj instanceof String) {
            // 处理JSON字符串
            String jsonStr = (String) dataObj;
            try {
                return JSONUtil.toBean(jsonStr, type);
            } catch (Exception e) {
                log.error("JSON字符串转对象失败: {}", jsonStr, e);
                return null;
            }
        }
        return null;
    }

    private <R, ID> R handleLegacyFormat(Map<Object, Object> cacheMap, String key,
                                         ID id, Class<R> type, Function<ID, R> dbFallback, Long expireTime, TimeUnit unit) {

        // 先返回传统格式的数据
        R result = convertHashToObject(cacheMap, type);

        // 异步转换为逻辑过期格式
        CACHE_REBUILD_EXECUTOR.submit(() -> {
            try {
                rebuildCache(key, id, dbFallback, expireTime, unit, null);
            } catch (Exception e) {
                log.error("异步重建缓存失败", e);
            }
        });

        return result;
    }

    private <R, ID> void rebuildCache(String key, ID id, Function<ID, R> dbFallback,
                                   Long expireTime, TimeUnit unit, String lockKey) {
        try {
            R result = dbFallback.apply(id);
            if (result != null) {
                // 正确的数据结构
                String dataJson = JSONUtil.toJsonStr(result);

                Map<String, String> cacheData = new HashMap<>();
                cacheData.put("data", dataJson);
                cacheData.put("expireTime", LocalDateTime.now().plusSeconds(unit.toSeconds(expireTime)).format(NORM_DATETIME_PATTERN));

                stringRedisTemplate.opsForHash().putAll(key, cacheData);
            } else {
                // 缓存空值
                stringRedisTemplate.opsForHash().put(key, "_NULL_", "1");
            }
        } catch (Exception e) {
            log.error("重建缓存失败", e);
        } finally {
            if (lockKey != null) {
                unLock(lockKey);
            }
        }
    }

    /**
     * 互斥锁解决缓存击穿（对于某个过期key进行大量访问）
     */
    /**
     * 加锁
     * @param key
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     * @param key
     */
    private void unLock(String key) {
        // 使用Lua脚本保证原子性，避免误释放其他线程的锁
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        stringRedisTemplate.execute(new DefaultRedisScript<>(script, Long.class),
                Collections.singletonList(key),
                stringRedisTemplate.opsForValue().get(key));
    }


    /**
     * 通用Hash转对象方法 - 支持泛型
     */
    private <R> R convertHashToObject(Map<Object, Object> hashMap, Class<R> type) {
        if (hashMap == null || hashMap.isEmpty()) {
            log.warn("Hash数据为空");
            return null;
        }

        try {
            // 先将所有值转换为String
            Map<String, Object> convertedMap = new HashMap<>();
            for (Map.Entry<Object, Object> entry : hashMap.entrySet()) {
                String field = entry.getKey().toString();
                Object value = entry.getValue();
                convertedMap.put(field, value != null ? value.toString() : null);
            }

            // 使用反射获取字段类型信息，实现智能转换
            return BeanUtil.mapToBean(convertedMap, type, true, createTypeAwareCopyOptions(type));

        } catch (Exception e) {
            log.error("Hash转对象失败", e);
            return null;
        }
    }

    /**
     * 创建类型感知的CopyOptions - 根据类字段类型自动转换
     */
    private <R> CopyOptions createTypeAwareCopyOptions(Class<R> type) {
        // 获取类的所有字段类型信息
        Field[] fields = type.getDeclaredFields();
        Map<String, Class<?>> fieldTypes = new HashMap<>();
        for (Field field : fields) {
            fieldTypes.put(field.getName(), field.getType());
        }

        return new CopyOptions()
                .ignoreNullValue()
                .setFieldValueEditor((fieldName, fieldValue) -> {
                    if (fieldValue == null) {
                        return null;
                    }

                    String strValue = fieldValue.toString();
                    if (strValue.isEmpty()) {
                        return null;
                    }

                    // 根据字段实际类型进行转换
                    Class<?> fieldType = fieldTypes.get(fieldName);
                    if (fieldType == null) {
                        return strValue; // 未知字段，保持字符串
                    }

                    try {
                        if (fieldType == Long.class || fieldType == long.class) {
                            return Long.parseLong(strValue);
                        } else if (fieldType == Integer.class || fieldType == int.class) {
                            return Integer.parseInt(strValue);
                        } else if (fieldType == Double.class || fieldType == double.class) {
                            return Double.parseDouble(strValue);
                        } else if (fieldType == Float.class || fieldType == float.class) {
                            return Float.parseFloat(strValue);
                        } else if (fieldType == Boolean.class || fieldType == boolean.class) {
                            return Boolean.parseBoolean(strValue);
                        } else if (fieldType == LocalDateTime.class) {
                            return LocalDateTime.parse(strValue, NORM_DATETIME_PATTERN);
                        } else if (fieldType == LocalDate.class) {
                            return LocalDate.parse(strValue);
                        }
                        // 其他类型保持字符串，或者可以继续扩展
                    } catch (Exception e) {
                        log.warn("字段类型转换失败: {} = {}, 目标类型: {}", fieldName, strValue, fieldType.getSimpleName());
                        return strValue; // 转换失败保持原值
                    }

                    return strValue;
                });
    }
}
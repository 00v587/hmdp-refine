package com.hmdp.utils.shopcache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.github.benmanes.caffeine.cache.Cache;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.constans.RedisConstants.CACHE_SHOP_KEY;

@Component
public class ShopRedisHandler implements InitializingBean {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IShopService shopService;


    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    static {
        // 注册JavaTimeModule以支持LocalDateTime等Java 8时间类型
        MAPPER.registerModule(new JavaTimeModule());
    }

    @Resource
    private Cache<String, Object> shopCache;

    // 缓存预热

    @Override
    public void afterPropertiesSet() throws Exception {
        try {
            // 初始化缓存
            // 1.查询商品信息
            List<Shop> shopList = shopService.list();
            // 2.放入缓存
            for (Shop shop : shopList) {
                // 2.1.item序列化为JSON
                String json = MAPPER.writeValueAsString(shop);
                // 2.2 存入caffeind
                String key = CACHE_SHOP_KEY + shop.getId();
                shopCache.put(key, shop);
                // 2.2.存入redis
                stringRedisTemplate.opsForValue().set(key, json);
            }
        } catch (Exception e) {
            // 如果出现任何异常（如表不存在），只打印日志，不中断应用启动
            System.err.println("缓存预热失败: " + e.getMessage());
            e.printStackTrace();
        }

//        // 3.查询商品库存信息
//        List<ItemStock> stockList = stockService.list();
//        // 4.放入缓存
//        for (ItemStock stock : stockList) {
//            // 2.1.item序列化为JSON
//            String json = MAPPER.writeValueAsString(stock);
//            // 2.2.存入redis
//            redisTemplate.opsForValue().set("item:stock:id:" + stock.getId(), json);
//        }
    }

    public void saveShop(Shop shop) {
        try {
            String json = MAPPER.writeValueAsString(shop);
            String key = CACHE_SHOP_KEY  + shop.getId();
            stringRedisTemplate.opsForValue().set(key , json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteShopById(Long id) {
        String key = CACHE_SHOP_KEY  + id;
        stringRedisTemplate.delete(key);
    }
}
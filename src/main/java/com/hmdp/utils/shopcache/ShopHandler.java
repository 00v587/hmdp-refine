package com.hmdp.utils.shopcache;

import com.github.benmanes.caffeine.cache.Cache;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.es.ShopSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.javatool.canal.client.annotation.CanalTable;
import top.javatool.canal.client.handler.EntryHandler;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import static com.hmdp.utils.constans.RedisConstants.CACHE_SHOP_KEY;

@CanalTable(value = "tb_shop")
@Component
@Slf4j
public class ShopHandler implements EntryHandler<Shop>{


    @Autowired
    private ShopRedisHandler redisHandler;

    @Resource
    private Cache<String, Object> shopCache;

    @Autowired
    private ShopSyncService shopSyncService;

    @PostConstruct
    public void init() {
        log.info("ShopHandler initialized, shopSyncService is null: {}", shopSyncService == null);
    }

    @Override
    public void insert(Shop shop) {
        log.info("Handling shop insert event, shop id: {}", shop.getId());
        log.debug("Shop data for insert: {}", shop);
        // 写数据到JVM进程缓存
        String key = CACHE_SHOP_KEY + shop.getId();
        shopCache.put(key , shop);
        // 写数据到redis
        redisHandler.saveShop(shop);
        // 写数据到 es
        if (shopSyncService != null) {
            try {
                shopSyncService.syncShopToES(shop);
                log.info("Successfully synced shop to ES, shop id: {}", shop.getId());
            } catch (Exception e) {
                log.error("Failed to sync shop to ES, shop id: {}", shop.getId(), e);
            }
        } else {
            log.warn("shopSyncService is null, skipping ES sync for shop insert, id: {}", shop.getId());
        }
    }

    @Override
    public void update(Shop before, Shop after) {
        log.info("Handling shop update event, shop id: {}", after.getId());
        log.debug("Shop data before update: {}", before);
        log.debug("Shop data after update: {}", after);
        // 写数据到JVM进程缓存
        String key = CACHE_SHOP_KEY + after.getId();
        shopCache.put(key, after);
        // 写数据到redis
        redisHandler.saveShop(after);
        // 写数据到 es
        if (shopSyncService != null) {
            try {
                shopSyncService.updateShopInES(after);
                log.info("Successfully updated shop in ES, shop id: {}", after.getId());
            } catch (Exception e) {
                log.error("Failed to update shop in ES, shop id: {}", after.getId(), e);
            }
        } else {
            log.warn("shopSyncService is null, skipping ES sync for shop update, id: {}", after.getId());
        }
    }

    @Override
    public void delete(Shop shop) {
        log.info("Handling shop delete event, shop id: {}", shop.getId());
        log.debug("Shop data for delete: {}", shop);
        // 删除数据到JVM进程缓存
        String key = CACHE_SHOP_KEY + shop.getId();
        shopCache.invalidate(key);
        // 删除数据到redis
        redisHandler.deleteShopById(shop.getId());
        // 删除数据到 es
        if (shopSyncService != null) {
            try {
                shopSyncService.deleteShopFromES(shop.getId());
                log.info("Successfully deleted shop from ES, shop id: {}", shop.getId());
            } catch (Exception e) {
                log.error("Failed to delete shop from ES, shop id: {}", shop.getId(), e);
            }
        } else {
            log.warn("shopSyncService is null, skipping ES sync for shop delete, id: {}", shop.getId());
        }
    }
}
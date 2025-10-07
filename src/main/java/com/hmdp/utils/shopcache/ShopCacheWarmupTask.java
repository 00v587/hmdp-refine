package com.hmdp.utils.shopcache;

import com.hmdp.service.IShopService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 商铺缓存预热定时任务
 * 每天凌晨执行缓存预热操作
 */
@Slf4j
@Component
public class ShopCacheWarmupTask {

    @Autowired
    private IShopService shopService;

    /**
     * 每天凌晨2点执行缓存预热
     * cron表达式: 秒 分 时 日 月 周
     * 0 0 2 * * ? 表示每天凌晨2点执行
     */
//    @Scheduled(cron = "0 0 2 * * ?")
//    public void warmupShopCache() {
//        log.info("开始执行商铺缓存预热任务...");
//
//        try {
//            // 获取所有商铺数据
//            List<Shop> shops = shopService.list();
//            log.info("共获取到 {} 条商铺数据", shops.size());
//
//            // 为每个商铺创建带逻辑过期时间的缓存
//            int successCount = 0;
//            for (Shop shop : shops) {
//                try {
//                    if (shop != null && shop.getId() != null) {
//                        shopService.saveShop2Redis(shop.getId(), 20L);
//                        successCount++;
//                    }
//                } catch (Exception e) {
//                    log.error("预热商铺ID {} 时发生异常", shop != null ? shop.getId() : "null", e);
//                }
//            }
//
//            log.info("商铺缓存预热任务完成，成功预热 {} 条数据", successCount);
//        } catch (Exception e) {
//            log.error("执行商铺缓存预热任务时发生异常", e);
//        }
//    }
}
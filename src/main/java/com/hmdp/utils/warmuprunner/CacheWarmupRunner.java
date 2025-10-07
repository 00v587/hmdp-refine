package com.hmdp.utils.warmuprunner;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 缓存预热类 - 应用启动时加载热点数据到Redis
 * 注意：此功能已通过定时任务实现，此处保留作为备用方案
 */
@Slf4j
@Component
public class CacheWarmupRunner implements ApplicationRunner {

    @Autowired
    private IShopService shopService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
//        log.info("应用启动时缓存预热已禁用，使用定时任务替代...");
        // 如果需要在启动时也进行预热，可以取消下面的注释

        log.info("开始缓存预热...");

        // 这里可以根据实际业务需求选择需要预热的热点数据
        // 预热所有店铺数据
        List<Shop> shops = shopService.list();

        // 为每个店铺创建带逻辑过期时间的缓存
        for (Shop shop : shops) {
            try {
                if (shop != null && shop.getId() != null) {
                    // 将过期时间从20秒改为30分钟(1800秒)
                    shopService.saveShop2Redis(shop.getId(), 1800L);
                } else {
                    log.info("发现空店铺数据或店铺ID为空");
                }
            } catch (Exception e) {
                System.err.println("预热店铺ID " + (shop != null ? shop.getId() : "null") + " 时发生异常: " + e.getMessage());
                e.printStackTrace();
            }
        }

        log.info("缓存预热完成，共预热" + shops.size() + "条店铺数据");

    }
}
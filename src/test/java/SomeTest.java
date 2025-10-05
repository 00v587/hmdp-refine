import com.hmdp.HmDianPingApplication;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest(classes = HmDianPingApplication.class)
public class SomeTest {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IShopService shopService;

    @Test
    public void loadGeoData() {
        // 1. 查询店铺信息
        List<Shop> shopList = shopService.list();
        // 2. 将店铺分类
        Map<Long, List<Shop>> map = shopList.stream()
                .collect(Collectors.groupingBy(Shop::getTypeId));
        // 3. 批量写入Redis
        for(Map.Entry<Long, List<Shop>> entry : map.entrySet()){
            // 3.1 获取类型id
            Long typeId = entry.getKey();
            String key = SHOP_GEO_KEY + typeId;
            // 3.2 获取店铺列表
            List<Shop> value = entry.getValue();
            // 创建名称和位置的映射
            for(Shop shop : value){
                Point point = new Point(shop.getX(), shop.getY());
                stringRedisTemplate.opsForGeo().add(key, point, shop.getId().toString());
            }
        }
    }
}
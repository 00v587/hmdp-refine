import com.hmdp.HmDianPingApplication;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootTest(classes = HmDianPingApplication.class)
class SomeTest {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IShopService shopService;

    @Autowired
    private RestHighLevelClient esClient;

//    @Test
//    public void loadGeoData() {
//        // 1. 查询店铺信息
//        List<Shop> shopList = shopService.list();
//        // 2. 将店铺分类
//        Map<Long, List<Shop>> map = shopList.stream()
//                .collect(Collectors.groupingBy(Shop::getTypeId));
//        // 3. 批量写入Redis
//        for(Map.Entry<Long, List<Shop>> entry : map.entrySet()){
//            // 3.1 获取类型id
//            Long typeId = entry.getKey();
//            String key = SHOP_GEO_KEY + typeId;
//            // 3.2 获取店铺列表
//            List<Shop> value = entry.getValue();
//            // 创建名称和位置的映射
//            for(Shop shop : value){
//                Point point = new Point(shop.getX(), shop.getY());
//                stringRedisTemplate.opsForGeo().add(key, point, shop.getId().toString());
//            }
//        }
//    }

    /**
     * 导入数据到ES索引
     */
    @Test
    public void importAllShopsToES() throws IOException {
        List<Shop> shops = shopService.list(); // 从数据库读取全部商铺

        for (Shop shop : shops) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", shop.getId());
            map.put("name", shop.getName());
            map.put("typeId", shop.getTypeId());
            map.put("images", shop.getImages());
            map.put("area", shop.getArea());
            map.put("address", shop.getAddress());
            map.put("avgPrice", shop.getAvgPrice());
            map.put("sold", shop.getSold());
            map.put("comments", shop.getComments());
            map.put("score", shop.getScore());
            map.put("location", shop.getY() + "," + shop.getX()); // 或者 "lat,lon"
            map.put("createTime", shop.getCreateTime());
            map.put("updateTime", shop.getUpdateTime());

            IndexRequest request = new IndexRequest("shops")
                    .id(shop.getId().toString())
                    .source(map);

            esClient.index(request, RequestOptions.DEFAULT);
        }

        System.out.println("✅ 已成功导入 " + shops.size() + " 条商铺数据到 ES！");
    }
}
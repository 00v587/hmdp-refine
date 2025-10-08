package com.hmdp.utils.es;

import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ShopSyncService {

    private final ShopMapper shopMapper;
    private final RestHighLevelClient esClient;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private ElasticsearchRestTemplate elasticsearchTemplate;
    
    @PostConstruct
    public void init() {
        log.info("ShopSyncService initialized");
        log.info("elasticsearchOperations is null: {}", elasticsearchOperations == null);
        log.info("elasticsearchTemplate is null: {}", elasticsearchTemplate == null);
        log.info("ES Client nodes: {}", esClient.getLowLevelClient().getNodes());
    }

    // 转换商铺数据为ES文档
    public ShopDocument convertToDocument(Shop shop) {
        log.debug("Converting shop to document, shop id: {}", shop.getId());
        ShopDocument document = new ShopDocument();
        document.setId(shop.getId());
        document.setName(shop.getName());
        document.setTypeId(shop.getTypeId());
        document.setImages(shop.getImages());
        document.setArea(shop.getArea());
        document.setAddress(shop.getAddress());

        // 关键：将x(经度),y(纬度)转换为ES地理位置格式
        // 格式: "纬度,经度" (注意顺序，与常见习惯相反)
        if (shop.getY() != null && shop.getX() != null) {
            document.setLocation(shop.getY() + "," + shop.getX());
            log.debug("Shop coordinates - x: {}, y: {}, location: {}", shop.getX(), shop.getY(), document.getLocation());
        } else {
            log.warn("Shop coordinates are null, shop id: {}", shop.getId());
        }

        document.setAvgPrice(shop.getAvgPrice());
        document.setSold(shop.getSold());
        document.setComments(shop.getComments());
        document.setScore(shop.getScore());
        document.setOpenHours(shop.getOpenHours());
        document.setCreateTime(shop.getCreateTime());
        document.setUpdateTime(shop.getUpdateTime());
        
        log.debug("Converted shop to document: {}", document);

        return document;
    }

    // 转换为Map用于ES索引
    public Map<String, Object> convertToMap(ShopDocument document) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", document.getId());
        map.put("name", document.getName());
        map.put("typeId", document.getTypeId());
        map.put("images", document.getImages());
        map.put("area", document.getArea());
        map.put("address", document.getAddress());
        map.put("location", document.getLocation()); // 地理位置字符串
        map.put("avgPrice", document.getAvgPrice());
        map.put("sold", document.getSold());
        map.put("comments", document.getComments());
        map.put("score", document.getScore());
        map.put("openHours", document.getOpenHours());
        map.put("createTime", document.getCreateTime());
        map.put("updateTime", document.getUpdateTime());
        return map;
    }

    /**
     * 使用ElasticsearchRestTemplate进行更精细的控制
     */
    public void updateShopInES(Shop shop) {
        log.info("updateShopInES called, shop id: {}, shop: {}", shop.getId(), shop);
        try {
            ShopDocument document = convertToDocument(shop);
            IndexQuery indexQuery = new IndexQueryBuilder()
                    .withId(shop.getId().toString())
                    .withObject(document)
                    .build();

            log.debug("Indexing shop document: {}", indexQuery);
            String result = elasticsearchTemplate.index(indexQuery, IndexCoordinates.of("shops"));
            log.info("Successfully updated shop in ES with id: {}, result: {}", shop.getId(), result);
            
            // 验证数据是否真的写入
            verifyShopInES(shop.getId());
        } catch (Exception e) {
            log.error("更新ES店铺失败, ID: {}", shop.getId(), e);
            throw e; // 重新抛出异常，让调用者知道操作失败
        }
    }
    
    /**
     * 同步数据到ES
     * @param shop
     */
    public void syncShopToES(Shop shop) {
        log.info("syncShopToES called, shop id: {}, shop: {}", shop.getId(), shop);
        try {
            ShopDocument document = this.convertToDocument(shop);
            elasticsearchOperations.save(document);
            log.info("Successfully synced shop to ES, shop id: {}", shop.getId());
            
            // 验证数据是否真的写入
            verifyShopInES(shop.getId());
        } catch (Exception e) {
            log.error("同步ES店铺失败, ID: {}", shop.getId(), e);
            throw e; // 重新抛出异常，让调用者知道操作失败
        }
    }

    /**
     * 删除ES中的商铺数据
     * @param shopId 商铺id
     */
    public void deleteShopFromES(Long shopId) {
        log.info("deleteShopFromES called, shop id: {}", shopId);
        try {
            elasticsearchOperations.delete(shopId.toString(), ShopDocument.class);
            log.info("Successfully deleted shop from ES, shop id: {}", shopId);
        } catch (Exception e) {
            log.error("删除ES店铺失败, ID: {}", shopId, e);
            throw e; // 重新抛出异常，让调用者知道操作失败
        }
    }
    
    /**
     * 验证店铺是否在ES中
     */
    private void verifyShopInES(Long shopId) {
        try {
            // 使用elasticsearchOperations验证
            ShopDocument document = elasticsearchOperations.get(shopId.toString(), ShopDocument.class);
            log.info("Verification with elasticsearchOperations - Shop in ES: {}", document != null);
            if (document != null) {
                log.debug("Retrieved document from ES with elasticsearchOperations: {}", document);
            }
            
            // 使用elasticsearchTemplate验证
            ShopDocument document2 = elasticsearchTemplate.get(shopId.toString(), ShopDocument.class, IndexCoordinates.of("shops"));
            log.info("Verification with elasticsearchTemplate - Shop in ES: {}", document2 != null);
            if (document2 != null) {
                log.debug("Retrieved document from ES with elasticsearchTemplate: {}", document2);
            }
            
            // 检查ES客户端连接信息
            log.info("ES Client nodes: {}", esClient.getLowLevelClient().getNodes());
            
            // 检查索引统计信息
            long count = elasticsearchOperations.count(Query.findAll(), ShopDocument.class);
            log.info("Total documents in shops index: {}", count);
        } catch (Exception e) {
            log.error("Failed to verify shop in ES, ID: {}", shopId, e);
        }
    }
}
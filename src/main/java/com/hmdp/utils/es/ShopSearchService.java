package com.hmdp.utils.es;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.ShopVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.index.query.GeoDistanceQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ShopSearchService {

    private final RestHighLevelClient esClient;

    /**
     * 搜索附近商铺
     */
    public SearchResponse searchNearbyShops(Double lat, Double lon, Double distance,
                                            Integer page, Integer size) {
        try {
            // 构建地理位置查询
            GeoDistanceQueryBuilder geoQuery = QueryBuilders.geoDistanceQuery("location")
                    .point(lat, lon)
                    .distance(distance, DistanceUnit.KILOMETERS);

            SearchRequest searchRequest = new SearchRequest("shops");
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

            sourceBuilder.query(geoQuery)
                    .sort(SortBuilders.geoDistanceSort("location", lat, lon)
                            .order(SortOrder.ASC)
                            .unit(DistanceUnit.KILOMETERS))
                    .from((page - 1) * size)
                    .size(size);

            searchRequest.source(sourceBuilder);
            return esClient.search(searchRequest, RequestOptions.DEFAULT);

        } catch (IOException e) {
            log.error("附近商铺搜索失败: {}", e.getMessage(), e);
            throw new RuntimeException("搜索服务暂不可用");
        }
    }

    /**
     * 解析搜索结果，包含距离信息
     */
    public List<ShopVO> parseSearchResult(SearchResponse response) {
        List<ShopVO> shops = new ArrayList<>();
        for (SearchHit hit : response.getHits().getHits()) {
            // 获取距离信息（排序值中的第一个）
            Object[] sortValues = hit.getSortValues();
            Double distance = sortValues.length > 0 ?
                    Double.parseDouble(sortValues[0].toString()) : null;

            // 解析商铺数据
            Map<String, Object> sourceMap = hit.getSourceAsMap();
            ShopVO shop = new ShopVO();
            
            // 手动映射字段，确保数据正确转换
            shop.setId(getLongValue(sourceMap, "id"));
            shop.setName(getStringValue(sourceMap, "name"));
            shop.setTypeId(getLongValue(sourceMap, "typeId"));
            shop.setImages(getStringValue(sourceMap, "images"));
            shop.setArea(getStringValue(sourceMap, "area"));
            shop.setAddress(getStringValue(sourceMap, "address"));
            
            // 从location字段解析经纬度
            String location = getStringValue(sourceMap, "location");
            if (location != null && !location.isEmpty()) {
                String[] coords = location.split(",");
                if (coords.length == 2) {
                    try {
                        shop.setX(Double.parseDouble(coords[0]));
                        shop.setY(Double.parseDouble(coords[1]));
                    } catch (NumberFormatException e) {
                        log.warn("解析经纬度失败: {}", location);
                    }
                }
            }
            
            shop.setAvgPrice(getLongValue(sourceMap, "avgPrice"));
            shop.setSold(getIntegerValue(sourceMap, "sold"));
            shop.setComments(getIntegerValue(sourceMap, "comments"));
            shop.setScore(getIntegerValue(sourceMap, "score"));
            shop.setOpenHours(getStringValue(sourceMap, "openHours"));
            
            // 设置距离
            shop.setDistance(distance);
            shops.add(shop);
        }

        return shops;
    }
    
    // 辅助方法：安全获取Long值
    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    // 辅助方法：安全获取Integer值
    private Integer getIntegerValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    // 辅助方法：安全获取String值
    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
}
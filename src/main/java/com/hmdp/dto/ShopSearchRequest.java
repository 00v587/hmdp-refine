package com.hmdp.dto;

import lombok.Data;

@Data
public class ShopSearchRequest {
    // 地理位置参数
    private Double lat;
    private Double lon;
    private Double distance = 5.0; // 默认5公里

    // 搜索参数
    private String keyword;
    private Long typeId;
    private Integer minPrice;
    private Integer maxPrice;
    private Integer minScore;

    // 排序参数
    private String sortBy; // score, sold, comments

    // 分页参数
    private Integer page = 1;
    private Integer size = 10;
}
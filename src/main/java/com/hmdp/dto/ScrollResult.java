package com.hmdp.dto;

import lombok.Data;

import java.util.List;

@Data
public class ScrollResult {
    private List<?> list;  // 博客列表
    private Long minTime;  // 最小时间戳
    private Integer offset; // 偏移量 跳过时间戳相同的博客
}

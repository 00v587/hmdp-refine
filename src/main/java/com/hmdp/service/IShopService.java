package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;

import java.io.IOException;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    /**
     * 根据id查询商铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    Result queryById(Long id);

    /**
     * 新增商铺信息
     * @param shop 商铺数据
     * @return 商铺id
     */
    Result update(Shop shop);

    /**
     * 缓存预热
     */
    void saveShop2Redis(Long id, long l);

    /**
     * 搜索附近商铺信息
     * @param type 商铺类型
     * @param sort 排序字段
     * @param lat 纬度
     * @param lon 经度
     * @param page 页码
     * @param size 页大小
     * @return 搜索结果
     */
    Result searchShops(String type, String sort, Double lat, Double lon, Integer page, Integer size) throws IOException;
}

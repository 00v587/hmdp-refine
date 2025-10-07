package com.hmdp.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import cn.hutool.json.JSONUtil;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.hmdp.utils.constans.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询商铺类型列表
     * @return 商铺类型列表
     */
    @Override
    public List<ShopType> queryTypeList() {
        String key = CACHE_SHOP_TYPE_KEY;

        String shopTypeJson = stringRedisTemplate.opsForValue().get(key);
        if (shopTypeJson != null) {
            try {
                List<ShopType> typeList = JSONUtil.toList(JSONUtil.parseArray(shopTypeJson), ShopType.class);
                // 从Redis获取的数据也需要排序
                if (typeList != null && !typeList.isEmpty()) {
                    typeList.sort(Comparator.comparingInt(ShopType::getSort));
                }
                return typeList;
            } catch (Exception e) {
                log.error("Redis数据解析失败", e);
            }
        }

        // 从数据库查询时添加排序
        List<ShopType> typeList = this.query().orderByAsc("sort").list();

        if (typeList == null || typeList.isEmpty()) {
            stringRedisTemplate.opsForValue().set(key, "[]", Duration.ofMinutes(5));
            return Collections.emptyList();
        }

        // 确保存入Redis的数据也是排序后的
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(typeList));
        return typeList;
    }
}

package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.SeckillVoucher;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2022-01-04
 */
public interface SeckillVoucherMapper extends BaseMapper<SeckillVoucher> {

    /**
     * 查询所有优惠券id
     * @return
     */
    @Select("select voucher_id from tb_seckill_voucher")
    List<Long> selectAllIds();
}

package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.impl.VoucherOrderServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Resource
    private VoucherOrderServiceImpl voucherOrderService;

    /**
     * 秒杀优惠券
     * @param voucherId 优惠券id
     * @return 订单id
     */
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        log.info("开始秒杀优惠券");
        return voucherOrderService.seckillVoucher(voucherId);
    }
}

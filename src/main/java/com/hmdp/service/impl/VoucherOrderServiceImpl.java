package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 秒杀优惠券
     * @param voucherId 优惠券id
     * @return 订单id
     */
    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        //TODO 超卖+一人一单

        // 1. 查优惠券是否存在
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2. 判断优惠券是否存在
        if(voucher == null){
            return Result.fail("优惠券不存在");
        }
        // 2. 秒杀是否开始或结束
        LocalDateTime now = LocalDateTime.now();

        if(now.isBefore(voucher.getBeginTime())){
            return Result.fail("还没到秒杀时间哟！");
        }
        if(now.isAfter(voucher.getEndTime())){
            return Result.fail("来晚啦秒杀时间已结束！");
        }

        // 2.1 在期间则查询库存
        // 2.2 不在期间则返回错误
        if(voucher.getStock() < 1){
            return Result.fail("库存不足");
        }

        Long userId = UserHolder.getUser().getId();

        //创建锁对象
        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        // 3. 尝试获取锁
        boolean isLock = lock.tryLock(1200L);
        // 3.1 获取锁失败则返回错误
        if(!isLock){
            return Result.fail("您已下单，请勿重复下单");
        }
        try {
            //获取代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }finally {
            // 3.2 释放锁
            lock.unLock();
        }
    }

    /**
     * 创建订单
     * @param voucherId 优惠券id
     * @return 订单id
     */
    @Override
    @Transactional
    public Result createVoucherOrder(Long voucherId) {

        // 先查询再扣减库存
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        
        // 判断库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }
        
        // 使用乐观锁更新库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)  // 乐观锁：确保库存大于0才更新
                .update();
        
        // 3.1 扣减失败则返回错误
        if (!success) {
            return Result.fail("库存不足");
        }
        
        // 3.2 扣减成功则返回订单id
        VoucherOrder voucherOrder = new VoucherOrder();
        // 3.1.1 订单id
        voucherOrder.setId(redisIdWorker.nextId("order"));
        // 3.1.2 用户id
        voucherOrder.setUserId(UserHolder.getUser().getId());
        // 3.1.3 优惠券id
        voucherOrder.setVoucherId(voucherId);

        save(voucherOrder);

        return Result.ok(voucherId);
    }

}

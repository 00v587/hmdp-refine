package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.message.OrderMessage;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.concurrent.ListenableFutureCallback;

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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private SeckillVoucherMapper seckillVoucherMapper;


    //创建阻塞队列
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newFixedThreadPool(5);
//
//    // 初始化消费者线程
//    {
//        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
//    }
    
//    private class VoucherOrderHandler implements Runnable{
//        @Override
//        public void run() {
//            while (true) {
//                try {
//                    // 1. 获取队列中的订单
//                    VoucherOrder voucherOrder = orderTasks.take();
//                    // 2. 创建订单
//                    handleVoucherOrder(voucherOrder);
//                } catch (Exception e) {
//                    log.error("处理订单异常", e);
//                }
//            }
//        }
//    }



    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }


    /**
     * 秒杀优惠券
     * @param voucherId 优惠券id
     * @return 订单id
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
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

        // 1. 执行lua脚本
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), UserHolder.getUser().getId().toString());
        int r = result.intValue();
        // 2. 判断是否为 0
        if(r != 0){
            switch (r){
                case 1:
                    return Result.fail("库存不足");
                case 2:
                    return Result.fail("不能重复下单");
            }
        }

        // 校验通过->发送消息到rabbitMQ
        OrderMessage message = new OrderMessage(UserHolder.getUser().getId(), voucherId);

        // 生产者发送消息-交换机 确认收到消息
        // 1. 创建correlationData
        CorrelationData correlationData = new CorrelationData();
        // 2. 给future添加confirmCallback
        correlationData.getFuture().addCallback(new ListenableFutureCallback<CorrelationData.Confirm>() {
            @Override
            public void onFailure(Throwable ex) {
                log.error("消息发送失败", ex);
            }

            @Override
            public void onSuccess(CorrelationData.Confirm result) {
                // 3.1 确认消息成功则添加到阻塞队列
                if(result.isAck()){
                    log.debug("消息发送成功");
                }else{
                    log.error("消息发送失败");
                }
            }
        });

        rabbitTemplate.convertAndSend("order.exchange", "order.create", message);

        // 3. 立即返回成功（异步下单）
        return Result.ok("刷新查看订单");
    }
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        // 1. 查优惠券是否存在
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        // 2. 判断优惠券是否存在
//        if(voucher == null){
//            return Result.fail("优惠券不存在");
//        }
//        // 2. 秒杀是否开始或结束
//        LocalDateTime now = LocalDateTime.now();
//
//        if(now.isBefore(voucher.getBeginTime())){
//            return Result.fail("还没到秒杀时间哟！");
//        }
//        if(now.isAfter(voucher.getEndTime())){
//            return Result.fail("来晚啦秒杀时间已结束！");
//        }
//
//        // 2.1 在期间则查询库存
//        // 2.2 不在期间则返回错误
//        if(voucher.getStock() < 1){
//            return Result.fail("库存不足");
//        }
//
//        Long userId = UserHolder.getUser().getId();
//
//        //创建锁对象
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        // 3. 尝试获取锁，设置等待时间和锁超时时间
//        boolean isLock = false;
//        try {
//            isLock = lock.tryLock(1, 10, java.util.concurrent.TimeUnit.SECONDS);
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            return Result.fail("系统繁忙，请稍后重试");
//        }
//
//        // 3.1 获取锁失败则返回错误
//        if(!isLock){
//            return Result.fail("您已下单，请勿重复下单");
//        }
//        try {
//            //获取代理对象
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }finally {
//            // 3.2 释放锁
//            if (lock.isHeldByCurrentThread()) {
//                lock.unlock();
//            }
//        }
//    }

    /**
     * 处理队列中的订单（异步执行）
     * @param voucherOrder 订单信息
     */
//    @Transactional
//    public void handleVoucherOrder(VoucherOrder voucherOrder) {
//        Long userId = voucherOrder.getUserId();
//        Long voucherId = voucherOrder.getVoucherId();
//
//        // 1. 查询是否已经下单（一人一单检查）
//        Long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
//        if (count > 0) {
//            log.error("用户{}重复下单优惠券{}", userId, voucherId);
//            return;
//        }
//
//        // 2. 同步更新数据库中的秒杀券库存
//        boolean success = seckillVoucherService.update()
//                .setSql("stock = stock - 1")
//                .eq("voucher_id", voucherId)
//                .gt("stock", 0) // 乐观锁，确保库存大于0
//                .update();
//
//        if (!success) {
//            log.error("更新秒杀券{}库存失败，可能库存不足", voucherId);
//            return;
//        }
//
//        // 3. 保存订单
//        save(voucherOrder);
//        log.info("订单{}创建成功，秒杀券{}库存已同步更新", voucherOrder.getId(), voucherId);
//    }

    /**
     * 创建普通优惠券订单（同步处理）
     * @param userId 用户id
     * @param voucherId 优惠券id
     */
    @Override
    @Transactional
    public void createVoucherOrder(Long userId, Long voucherId) {
        // 再做一次券有效性检查
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher == null || voucher.getStock() <= 0) {
            throw new RuntimeException("秒杀券已失效或无库存");
        }
        
        // 再做一次幂等性检查（双保险）
        long count = this.count(new QueryWrapper<VoucherOrder>()
                .eq("user_id", userId).eq("voucher_id", voucherId));
        if (count > 0) {
            log.warn("用户{}已下单，跳过重复下单: userId={}, voucherId={}", userId, userId, voucherId);
            return; // 返回而不抛出异常
        }

        // 扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0) // 乐观锁，确保库存大于0
                .update();
        
        if (!success) {
            log.error("更新秒杀券{}库存失败，可能库存不足", voucherId);
            throw new RuntimeException("库存不足");
        }

        // 保存订单
        VoucherOrder order = new VoucherOrder();
        order.setId(redisIdWorker.nextId("order"));
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        order.setCreateTime(LocalDateTime.now());
        this.save(order);
        log.info("订单创建成功，订单ID: {}, 用户ID: {}, 优惠券ID: {}", order.getId(), userId, voucherId);
    }

}
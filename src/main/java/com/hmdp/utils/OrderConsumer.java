package com.hmdp.utils;

import com.hmdp.entity.SeckillVoucher;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.OrderMessage;

import lombok.extern.slf4j.Slf4j;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.google.common.util.concurrent.RateLimiter;
import com.rabbitmq.client.Channel;
import java.io.IOException;

@Slf4j
@Component
public class OrderConsumer {

    @Autowired
    private IVoucherOrderService orderService;

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RabbitTemplate rabbitTemplate;
    
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    
    @Autowired
    private RateLimiter rateLimiter; // 注入限流器

    @RabbitListener(queues = "order.queue", concurrency = "5")
    public void handleOrder(OrderMessage msg, Channel channel, Message message) throws IOException {
        // 使用限流器，控制处理速率
        rateLimiter.acquire();
        
        try {
            // 先检查秒杀券是否还有效以及是否有库存
            SeckillVoucher voucher = seckillVoucherService.getById(msg.getVoucherId());
            if (voucher == null) {
                log.warn("秒杀券已不存在，拒绝处理订单: userId={}, voucherId={}", msg.getUserId(), msg.getVoucherId());
                // 直接确认消息，不再重新入队
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }

            // 检查是否在有效期内
            if (voucher.getBeginTime().isAfter(java.time.LocalDateTime.now())) {
                log.warn("秒杀券尚未开始，拒绝处理订单: userId={}, voucherId={}", msg.getUserId(), msg.getVoucherId());
                // 直接确认消息，不再重新入队
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }

            if (voucher.getEndTime().isBefore(java.time.LocalDateTime.now())) {
                log.warn("秒杀券已结束，拒绝处理订单: userId={}, voucherId={}", msg.getUserId(), msg.getVoucherId());
                // 直接确认消息，不再重新入队
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }

            // 检查库存
            if (voucher.getStock() <= 0) {
                log.warn("秒杀券库存不足，拒绝处理订单: userId={}, voucherId={}", msg.getUserId(), msg.getVoucherId());
                // 直接确认消息，不再重新入队
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }

            // 添加重试机制
            int maxRetries = 3;
            boolean success = false;
            Exception lastException = null;
            for (int i = 0; i < maxRetries && !success; i++) {
                try {
                    orderService.createVoucherOrder(msg.getUserId(), msg.getVoucherId());
                    success = true;
                } catch (Exception e) {
                    lastException = e;
                    if (i == maxRetries - 1) {
                        // 最后一次重试仍然失败
                        break;
                    }
                    // 等待一段时间后重试
                    try {
                        Thread.sleep(1000 * (i + 1));
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("线程中断", ex);
                    }
                }
            }

            if (!success) {
                // 所有重试都失败了
                throw lastException;
            }

            // 手动确认消息
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            log.info("订单处理成功: userId={}, voucherId={}", msg.getUserId(), msg.getVoucherId());
        } catch (Exception e) {
            // 记录日志，做补偿措施
            log.error("下单失败: userId={}, voucherId={}", msg.getUserId(), msg.getVoucherId(), e);
            
            // 补偿：恢复Redis库存
            try {
                stringRedisTemplate.opsForValue().increment(
                    "seckill:stock:" + msg.getVoucherId(), 1);
                log.info("已恢复Redis库存: userId={}, voucherId={}", 
                    msg.getUserId(), msg.getVoucherId());
            } catch (Exception redisException) {
                log.error("恢复Redis库存失败: userId={}, voucherId={}", 
                    msg.getUserId(), msg.getVoucherId(), redisException);
            }
            
            try {
                // 拒绝消息并重新入队（最多重新入队一次）
                channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
            } catch (IOException ioException) {
                log.error("确认消息失败", ioException);
            }
        }
    }
}
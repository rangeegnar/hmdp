package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.User;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    /*
    阻塞队列 BlockingQueue 是接口 ArrayBlockingQueue是实现
     */
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    /*
    线程池
     */
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    private IVoucherOrderService proxy;

    /**
     * 当前类初始化完毕就立马执行该方法
     */
    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandle());
    }
    private class VoucherOrderHandle implements Runnable{
        @Override
        public void run() {
            while (true){
                try{
                    VoucherOrder voucherOrder = orderTasks.take();
                    handleVoucherOrder(voucherOrder);
                }catch (Exception e){
                    log.error("处理订单异常", e);
                }
            }
        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder){
        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean tryLock = lock.tryLock();
        if(!tryLock){
            log.error("一人只能下一单");
            return;
        }
        try{
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }

    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long result = null;
        try {
            result = stringRedisTemplate.execute(
                    SECKILL_SCRIPT,
                    Collections.emptyList(),
                    voucherId.toString(),
                    UserHolder.getUser().getId().toString()
            );
        }catch (Exception e){
            log.error("lua执行错误");
            throw new RuntimeException(e);
        }
        if (result != null && !result.equals(0L)){
            int r = result.intValue();
            return Result.fail(r == 1 ? "库存不足": "不能重复下单");
        }
        // 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");   // 订单id
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(UserHolder.getUser().getId());    // 用户id
        voucherOrder.setVoucherId(voucherId);                    // voucher id

        proxy = (IVoucherOrderService) AopContext.currentProxy();
        orderTasks.add(voucherOrder);
        return Result.ok();
    }
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        // 1、判断当前用户是否是第一单
        int count = this.count(new LambdaQueryWrapper<VoucherOrder>()
                .eq(VoucherOrder::getUserId, userId));
        if (count >= 1) {
            // 当前用户不是第一单
            log.error("当前用户不是第一单");
            return;
        }
        // 2、用户是第一单，可以下单，秒杀券库存数量减一
        boolean flag = seckillVoucherService.update(new LambdaUpdateWrapper<SeckillVoucher>()
                .eq(SeckillVoucher::getVoucherId, voucherId)
                .gt(SeckillVoucher::getStock, 0)
                .setSql("stock = stock -1"));
        if (!flag) {
            throw new RuntimeException("秒杀券扣减失败");
        }
        // 3、将订单保存到数据库
        flag = this.save(voucherOrder);
        if (!flag) {
            throw new RuntimeException("创建秒杀券订单失败");
        }
    }

    //    @Override
//    public Result seckillVoucher(Long voucherId) {
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);  // mybatis plus
//        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
//            return Result.fail("秒杀还没开始");
//        }
//        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
//            return Result.fail("秒杀已经结束");
//        }
//        if(voucher.getStock() < 1){
//            return Result.fail("库存不足");
//        }
//        Long userId = UserHolder.getUser().getId();
//
//        // 获取锁
//        // SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, "order" + userId);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        boolean tryLock = lock.tryLock();
//        if (!tryLock) {
//            return Result.fail("不允许重复下单");
//        }
//
//        try {
//            synchronized (userId.toString().intern()){
//                IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//                return proxy.createVoucherOrder(voucherId);
//            }
//        }finally {
//            lock.unlock();
//        }
//    }
//    @Transactional
//    public Result createVoucherOrder(Long voucherId) {
//        // 实现一人一单
//        Long userId = UserHolder.getUser().getId();
////        这种做法先释放锁后提交事务
////        synchronized (userId.toString().intern()){
////            Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
////            if(count > 0){
////                return Result.fail("该用户已经购买过");
////            }
////            // 扣减库存
////            boolean success = seckillVoucherService.update().setSql("stock = stock - 1")            // set stock = stock - 1
////                    .eq("voucher_id", voucherId).gt("stock", 0)    // where id = ? and stock > 0
////                    .update();
////            if(!success){
////                return Result.fail("库存不足");
////            }
////            // 创建订单
////            VoucherOrder voucherOrder = new VoucherOrder();
////            long orderId = redisIdWorker.nextId("order");
////            voucherOrder.setId(orderId);
////            voucherOrder.setUserId(userId);
////            voucherOrder.setVoucherId(voucherId);
////            save(voucherOrder);
////            return Result.ok(orderId);
////        }
//        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
//        if(count > 0){
//            return Result.fail("该用户已经购买过");
//        }
//        // 扣减库存
//        boolean success = seckillVoucherService.update().setSql("stock = stock - 1")            // set stock = stock - 1
//                .eq("voucher_id", voucherId).gt("stock", 0)    // where id = ? and stock > 0
//                .update();
//        if(!success){
//            return Result.fail("库存不足");
//        }
//        // 创建订单
//        VoucherOrder voucherOrder = new VoucherOrder();
//        long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        voucherOrder.setUserId(userId);
//        voucherOrder.setVoucherId(voucherId);
//        save(voucherOrder);
//        return Result.ok(orderId);
//    }
}

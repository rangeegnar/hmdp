package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.constant.RedisConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;

    /* =========================================================================
     * 一、 🛡️ 缓存穿透解决方案（Cache Penetration）
     * 痛点：查询mysql和redis中都不存在的数据，高并发直接穿透到 DB。
     * ========================================================================= */

    // 1：缓存空对象（""）+ 2 分钟短 TTL
    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1. 尝试从 Redis 查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. 命中有效数据，直接反序列化返回
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        // 3. 判断是否命中空值标记（json != null 意味着是空字符串 ""）
        if (json != null) {
            return null;
        }

        // 4. Redis 完全未命中，根据 id 查询数据库
        R r = dbFallback.apply(id);
        // 5. 数据库也不存在，写入空值到 Redis，并设置短 TTL（2分钟）防穿透
        if (r == null) {
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 6. 数据库存在，写入正常缓存并返回
        this.set(key, r, time, unit);
        return r;
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    // 2：Redisson 布隆过滤器白名单拦截 + 空值兜底（第二道防线直接复用 queryWithPassThrough）
    // 布隆过滤器由 utils.BloomFilterUse 在项目启动时初始化并预热（只执行一次），此处只负责判定与兜底
    public <R, ID> R queryWithPassThroughBloom(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback,
            Long time, TimeUnit unit, String bloomFilterName) {
        try {
            // 1. 第一道防线：先通过布隆过滤器判断数据是否存在
            RBloomFilter<ID> bloomFilter = redissonClient.getBloomFilter(bloomFilterName);
            // 1.1 兜底：过滤器未初始化（启动预热失败 / Redis 被清空）时不可用，降级为空值缓存方案
            if (!bloomFilter.isExists()) {
                log.warn("布隆过滤器 {} 未初始化/不可用，本次查询降级为空值缓存方案", bloomFilterName);
                return queryWithPassThrough(keyPrefix, id, type, dbFallback, time, unit);
            }
            if (!bloomFilter.contains(id)) {
                // 布隆过滤器判定不存在，则一定不存在！直接拦截，绝不查 Redis 和 MySQL
                log.info("布隆过滤器判定数据不存在，拦截请求: {}{}", keyPrefix, id);
                return null;
            }
        } catch (Exception e) {
            // 1.2 兜底：Redis 抖动 / 过滤器被删除等异常，一律降级为空值缓存方案，避免接口 500
            log.error("布隆过滤器 {} 检查异常，本次查询降级为空值缓存方案, err: {}", bloomFilterName, e.toString());
            return queryWithPassThrough(keyPrefix, id, type, dbFallback, time, unit);
        }
        // 2. 第二道防线：布隆过滤器误判
        return queryWithPassThrough(keyPrefix, id, type, dbFallback, time, unit);
    }


    /* =========================================================================
     * 二、 ⚡ 缓存击穿解决方案（Cache Breakdown）
     * 痛点：超高并发访问的热点 Key 突然失效，大量并发线程瞬时冲击 DB 重建缓存。
     * ========================================================================= */

    // 1：分布式互斥锁 + 自旋重试 + Double Check（强一致性 CP 方案）
    public <R, ID> R queryWithMutex(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1. 尝试从 Redis 查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. 命中有效数据，直接返回
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        // 判断是否命中了空值缓存（防穿透）
        if (json != null) {
            return null;
        }

        // 3. 实现缓存重建
        // 3.1 获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        R r = null;
        try {
            boolean isLock = tryLock(lockKey);
            // 3.2 判断是否获取锁成功
            if (!isLock) {
                // 3.3 获取锁失败：休眠并递归重试（自旋等待其他线程重建完成）
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallback, time, unit);
            }
            // 3.4 获取锁成功：执行 Double Check，确认是否已有并发线程刚完成重建
            String doubleCheckJson = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(doubleCheckJson)) {
                return JSONUtil.toBean(doubleCheckJson, type);
            }
            if (doubleCheckJson != null) {
                return null;
            }

            // 3.5 查数据库
            r = dbFallback.apply(id);
            // 3.6 数据库不存在，写入空值防穿透
            if (r == null) {
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 3.7 数据库存在，写入 Redis 正常缓存
            this.set(key, r, time, unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 4. 释放互斥锁
            unLock(lockKey);
        }
        return r;
    }
    // 1. 1分布式互斥锁原语：尝试获取锁
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    // 1. 2分布式互斥锁原语：释放锁
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }



    // 2：逻辑过期时间 + 独立线程池异步重建（高吞吐 AP 方案）
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1. 尝试从 Redis 查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断缓存是否存在（热点数据必须已预热，未命中直接返回 null）
        if (StrUtil.isBlank(json)) {
            return null;
        }

        // 3. 命中缓存：反序列化为 RedisData 包装类
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R shop = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 4. 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 4.1 未过期，直接返回最新的店铺数据
            return shop;
        }

        // 4.2 已过期，需要进行缓存重建
        // 5. 尝试获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);

        // 6. 判断是否获取锁成功
        if (isLock) {
            // 6.1 成功，开启后台独立线程异步实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 查询数据库
                    R r1 = dbFallback.apply(id);
                    // 写入带逻辑过期时间的 Redis
                    this.setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unLock(lockKey);
                }
            });
        }

        // 6.2 主线程无论是获取锁成功还是失败，均直接降级返回过期的旧数据，保证毫秒级可用性
        return shop;
    }

    // 2，1线程池——逻辑过期
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    // 2. 2封装进 RedisData，Redis 物理永不过期
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 写入 Redis，物理不设 TTL
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


    /* =========================================================================
     * 三、 🌊 缓存雪崩解决方案（Cache Avalanche）
     * 痛点：大量 Key 集中在同一时间过期，或 Redis 宕机导致全部流量直冲数据库。
     * ========================================================================= */

    // 1：TTL 随机 Jitter 打散 + Redis 宕机容灾降级
    public <R, ID> R queryWithAvalanchePrevention(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback,
            Long baseTime, Long randomRange, TimeUnit unit) {
        String key = keyPrefix + id;
        try {
            // 1. 查 Redis 缓存
            String json = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(json)) {
                return JSONUtil.toBean(json, type);
            }
            if (json != null) {
                return null;
            }
        } catch (Exception e) {
            // 容灾设计：若 Redis 节点宕机或网络中断，记录日志并优雅降级直接查库，避免雪崩导致系统崩溃
            log.error("Redis 连接异常，触发防雪崩降级查询数据库, key: {}", key, e);
            return dbFallback.apply(id);
        }

        // 2. 查数据库
        R r = dbFallback.apply(id);
        if (r == null) {
            // 结合防穿透写入空值
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 3. 写入 Redis：使用带随机扰动的 TTL，将原本可能同一秒失效的海量 key 均匀分散在不同时间点过期
        this.setWithRandomTTL(key, r, baseTime, randomRange, unit);
        return r;
    }


    public void setWithRandomTTL(String key, Object value, Long baseTime, Long randomRange, TimeUnit unit) {
        long baseSeconds = unit.toSeconds(baseTime);
        long randomSeconds = ThreadLocalRandom.current().nextLong(1, Math.max(2, randomRange + 1));
        long totalSeconds = baseSeconds + randomSeconds;
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), totalSeconds, TimeUnit.SECONDS);
    }
}

package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.constant.RedisConstants;
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
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;

    /**
     * 线程池：专供【缓存击穿-逻辑过期方案】的后台异步重建使用
     */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate, RedissonClient redissonClient) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redissonClient = redissonClient;
    }

    /* =========================================================================
     * 一、 🛡️ 缓存穿透解决方案（Cache Penetration）
     * 痛点：查询一个数据库和缓存中都不存在的数据，高并发直接穿透到 DB。
     * ========================================================================= */

    /**
     * 方案 1：缓存空对象（""）+ 2 分钟短 TTL
     *
     * @param keyPrefix  缓存 key 前缀
     * @param id         主键 ID
     * @param type       返回实体类型
     * @param dbFallback 数据库回源查询逻辑
     * @param time       缓存有效时长
     * @param unit       时间单位
     * @param <R>        返回值类型
     * @param <ID>       ID 类型
     * @return 业务数据或 null
     */
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

    /**
     * 方案 2：Redisson 布隆过滤器白名单拦截 + 空值双重兜底
     *
     * @param keyPrefix        缓存 key 前缀
     * @param id               主键 ID
     * @param type             返回实体类型
     * @param dbFallback       数据库回源查询逻辑
     * @param time             缓存有效时长
     * @param unit             时间单位
     * @param bloomFilterName  布隆过滤器名称
     * @param <R>              返回值类型
     * @param <ID>             ID 类型
     * @return 业务数据或 null
     */
    public <R, ID> R queryWithPassThroughBloom(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback,
            Long time, TimeUnit unit, String bloomFilterName) {
        // 1. 第一道防线：先通过布隆过滤器判断数据是否存在
        RBloomFilter<ID> bloomFilter = redissonClient.getBloomFilter(bloomFilterName);
        if (!bloomFilter.contains(id)) {
            // 布隆过滤器判定不存在，则一定不存在！直接拦截，绝不查 Redis 和 MySQL
            log.info("布隆过滤器判定数据不存在，拦截请求: {}{}", keyPrefix, id);
            return null;
        }

        String key = keyPrefix + id;
        // 2. 第二道防线：尝试从 Redis 查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        // 判断是否命中空值（应对布隆过滤器误判过的 key，防止反复查库）
        if (json != null) {
            return null;
        }

        // 3. Redis 未命中，根据 id 查询数据库
        R r = dbFallback.apply(id);
        if (r == null) {
            // 数据库中不存在，说明发生了布隆过滤器的误判（False Positive）！
            // 写入空字符串并设置较短 TTL 作为兜底保护，避免该 key 持续击打数据库
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 4. 数据库存在，写入 Redis 正常缓存并返回
        this.set(key, r, time, unit);
        return r;
    }

    /**
     * 布隆过滤器辅助工具：初始化位图容量与误判率
     *
     * @param bloomFilterName    布隆过滤器名称
     * @param expectedInsertions 预估元素量
     * @param falseProbability   容错率 / 误判率（例如 0.01 表示 1% 误判率）
     */
    public <T> RBloomFilter<T> initBloomFilter(String bloomFilterName, long expectedInsertions, double falseProbability) {
        RBloomFilter<T> bloomFilter = redissonClient.getBloomFilter(bloomFilterName);
        bloomFilter.tryInit(expectedInsertions, falseProbability);
        return bloomFilter;
    }

    /**
     * 布隆过滤器辅助工具：向布隆过滤器添加合法 ID 白名单
     *
     * @param bloomFilterName 布隆过滤器名称
     * @param value           要添加的值
     */
    public <T> boolean addToBloomFilter(String bloomFilterName, T value) {
        RBloomFilter<T> bloomFilter = redissonClient.getBloomFilter(bloomFilterName);
        return bloomFilter.add(value);
    }


    /* =========================================================================
     * 二、 ⚡ 缓存击穿解决方案（Cache Breakdown）
     * 痛点：超高并发访问的热点 Key 突然失效，大量并发线程瞬时冲击 DB 重建缓存。
     * ========================================================================= */

    /**
     * 方案 1：分布式互斥锁 + 自旋重试 + Double Check（强一致性 CP 方案）
     *
     * @param keyPrefix  缓存 key 前缀
     * @param id         主键 ID
     * @param type       返回实体类型
     * @param dbFallback 数据库回源查询逻辑
     * @param time       缓存有效时长
     * @param unit       时间单位
     * @param <R>        返回值类型
     * @param <ID>       ID 类型
     * @return 业务数据或 null
     */
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

    /**
     * 方案 2：逻辑过期时间 + 独立线程池异步重建（高吞吐 AP 方案）
     *
     * @param keyPrefix  缓存 key 前缀
     * @param id         主键 ID
     * @param type       返回实体类型
     * @param dbFallback 数据库回源查询逻辑
     * @param time       缓存有效时长
     * @param unit       时间单位
     * @param <R>        返回值类型
     * @param <ID>       ID 类型
     * @return 业务数据或 null
     */
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

    /**
     * 逻辑过期写入前置方法：封装进 RedisData，Redis 物理永不过期
     *
     * @param key   缓存 key
     * @param value 业务对象
     * @param time  逻辑有效时长
     * @param unit  时间单位
     */
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

    /**
     * 方案 1：TTL 随机 Jitter 打散 + Redis 宕机容灾降级
     *
     * @param keyPrefix   缓存 key 前缀
     * @param id          主键 ID
     * @param type        返回实体类型
     * @param dbFallback  数据库回源查询逻辑
     * @param baseTime    基础有效时长
     * @param randomRange 随机扰动秒数范围（例如 300 表示在基础时间上随机增加 1~300 秒）
     * @param unit        时间单位
     * @param <R>         返回值类型
     * @param <ID>        ID 类型
     * @return 业务数据或 null
     */
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

    /**
     * 防雪崩基础写入：在基础 TTL 上叠加随机扰动（Jitter），分散过期时间
     *
     * @param key         缓存 key
     * @param value       缓存值
     * @param baseTime    基础有效时长
     * @param randomRange 随机浮动区间（如 1~randomRange 之间的随机秒数）
     * @param unit        基础时间单位
     */
    public void setWithRandomTTL(String key, Object value, Long baseTime, Long randomRange, TimeUnit unit) {
        long baseSeconds = unit.toSeconds(baseTime);
        long randomSeconds = ThreadLocalRandom.current().nextLong(1, Math.max(2, randomRange + 1));
        long totalSeconds = baseSeconds + randomSeconds;
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), totalSeconds, TimeUnit.SECONDS);
    }


    /* =========================================================================
     * 四、 🔧 底层基础缓存读写与分布式互斥锁原语
     * ========================================================================= */

    /**
     * 普通物理 TTL 写入 Redis
     *
     * @param key   缓存 key
     * @param value 缓存值
     * @param time  物理有效时长
     * @param unit  时间单位
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 获取互斥锁（SETNX + 10s 原子超时防死锁）
     *
     * @param key 锁的 key
     * @return 是否抢锁成功
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放互斥锁
     *
     * @param key 锁的 key
     */
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }
}

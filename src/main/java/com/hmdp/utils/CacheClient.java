package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
@Slf4j
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


    public <R, ID> R queryShopPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 1. 存在，直接返回
        if(StrUtil.isNotBlank(json)){
            return JSONUtil.toBean(json, type);
        }
        // 2. 找到了但是是空值（下面数据库插入""的情况）
        if(json != null){
            return null;
        }
        // 3. 不存在,根据id查询数据库
        R r = dbFallback.apply(id);
        if(r == null){
            // 4. 将空值写入redis
            stringRedisTemplate.opsForValue().set(key, "", time, unit);
            return null;
        }
        // stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(r), time, unit); 改为下面
        set(key, r, time, unit);

        return r;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = java.util.concurrent.Executors.newFixedThreadPool(10);
    public <R> R queryShopWithLogicalExpire(String keyPrefix, Long id, Class<R> type, Function<Long, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1. 从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否存在，不存在返回null
        if(StrUtil.isBlank(json)){
            return null;
        }
        // 3. 命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);

        LocalDateTime expireTime = redisData.getExpireTime();
        // 4. 判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            // 5.1. 未过期，直接返回
            return r;
        }
        // 5.2 过期，需要缓存重建
        // 5.2.1 获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean tryLock = tryLock(lockKey);
        // 5.2.2 判断是否获取锁成功
        if(tryLock){
            // 5.2.3 成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() ->{
                try {
                    R new_r = dbFallback.apply(id);
                    this.setWithLogicalExpire(key, new_r, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 5.2.4 释放锁
                    unlock(lockKey);
                }
            });

        }
        // 5.2.5 没有获取到锁，返回过期的商铺信息
        return r;
    }
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

}

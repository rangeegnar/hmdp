package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    private static final long COUNT_BITS = 32;
    public long nextId(String KeyPerfix){
        // 1. 生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long second = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = second - BEGIN_TIMESTAMP;
        // 2. 生成自增
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Long count = stringRedisTemplate.opsForValue().increment("inc" + KeyPerfix + ":" + date);
        // 3. 拼接并返回
        return timestamp << COUNT_BITS | count;

    }

    public static void main(String[] args) {
        LocalDateTime time = LocalDateTime.of(2022, 1, 1, 0, 0, 0);
        long second = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println(second);
    }
}

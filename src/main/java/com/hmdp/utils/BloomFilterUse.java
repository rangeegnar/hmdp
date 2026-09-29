package com.hmdp.utils;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.hmdp.constant.RedisConstants.BLOOM_SHOP_KEY;

/**
 * 布隆过滤器工具类（原 config.BloomFilterInitializer + CacheClient.initBloomFilter/addToBloomFilter 合并至此）
 *
 * 一、启动初始化（实现 ApplicationRunner，项目启动时只执行一次）：
 * 1. 调用 initBloomFilter 初始化布隆过滤器位图（预估10万数据、1%误判率）
 * 2. 扫描 MySQL 全部商铺 id，调用 addToBloomFilter 逐个写入过滤器
 * 3. 初始化失败仅记录日志、不阻塞应用启动；此时 queryWithPassThroughBloom 会自动降级为空值缓存方案
 *
 * 二、对外提供布隆过滤器辅助方法（由 CacheClient.queryWithPassThroughBloom 的判定及其他业务复用）：
 * - initBloomFilter：初始化位图容量与误判率。底层 tryInit 是幂等的，Redis 中已初始化过的过滤器
 *   不会被重建、已有数据不丢失，因此应用重启不会重复清空/重建位图。
 * - addToBloomFilter：添加元素到布隆过滤器
 *
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BloomFilterUse implements ApplicationRunner {

    private final RedissonClient redissonClient;
    private final IShopService shopService;

    /** 预估商铺数据量 */
    private static final long EXPECTED_INSERTIONS = 100_000L;
    /** 允许误判率 */
    private static final double FALSE_PROBABILITY = 0.01D;

    @Override
    public void run(ApplicationArguments args) {
        try {
            // 1. 初始化布隆过滤器位图（幂等）
            initBloomFilter(BLOOM_SHOP_KEY, EXPECTED_INSERTIONS, FALSE_PROBABILITY);

            // 2. 扫描 MySQL 全部商铺 id，预热写入布隆过滤器
            List<Shop> shops = shopService.list();
            for (Shop shop : shops) {
                addToBloomFilter(BLOOM_SHOP_KEY, shop.getId());
            }
            log.info("布隆过滤器 {} 初始化完成，已预热 {} 个商铺 id", BLOOM_SHOP_KEY, shops.size());
        } catch (Exception e) {
            // 初始化失败仅记日志，不阻塞启动；查询时自动降级为空值缓存方案
            log.error("布隆过滤器 {} 初始化失败，queryById 将自动降级为空值缓存方案(穿透方案)，err: {}",
                    BLOOM_SHOP_KEY, e.toString(), e);
        }
    }

    // 布隆过滤器辅助工具：初始化位图容量与误判率
    public <T> RBloomFilter<T> initBloomFilter(String bloomFilterName, long expectedInsertions, double falseProbability) {
        RBloomFilter<T> bloomFilter = redissonClient.getBloomFilter(bloomFilterName);
        bloomFilter.tryInit(expectedInsertions, falseProbability);
        return bloomFilter;
    }

    // 布隆过滤器辅助工具：添加元素到布隆过滤器
    public <T> boolean addToBloomFilter(String bloomFilterName, T value) {
        RBloomFilter<T> bloomFilter = redissonClient.getBloomFilter(bloomFilterName);
        return bloomFilter.add(value);
    }
}
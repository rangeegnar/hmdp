package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.constant.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.hmdp.constant.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private CacheClient client;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private ExecutorService es= Executors.newFixedThreadPool(500);


    @Test
    void testSaveShop() throws InterruptedException {
        Shop shop = shopService.getById(1L);
        client.setWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY+1L,shop,30L, TimeUnit.MINUTES);

    }

    @Test
    void testBloomFilter() {
        // 1. 初始化布隆过滤器（预估10万数据，1%误判率）
        client.initBloomFilter(RedisConstants.BLOOM_SHOP_KEY, 100000L, 0.01);

        // 2. 预热真实存在的商铺 ID (如 1L)
        client.addToBloomFilter(RedisConstants.BLOOM_SHOP_KEY, 1L);

        // 3. 正常查询已存在的商铺 (1L)
        Shop shop = client.queryWithPassThroughBloom(
                RedisConstants.CACHE_SHOP_KEY, 1L, Shop.class,
                shopService::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES,
                RedisConstants.BLOOM_SHOP_KEY
        );
        System.out.println("查询合法店铺结果: " + shop);

        // 4. 测试恶意不存在的商铺 (999999L)，布隆过滤器将直接拦截，不查 Redis 与 DB
        Shop notExistShop = client.queryWithPassThroughBloom(
                RedisConstants.CACHE_SHOP_KEY, 999999L, Shop.class,
                shopService::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES,
                RedisConstants.BLOOM_SHOP_KEY
        );
        System.out.println("查询不存在店铺结果(应为 null 且被布隆过滤器拦截): " + notExistShop);
    }

    @Test
    void testMutexAndAvalanche() {
        // 1. 测试互斥锁解决缓存击穿 (queryWithMutex)
        Shop shopMutex = client.queryWithMutex(
                RedisConstants.CACHE_SHOP_KEY, 1L, Shop.class,
                shopService::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES
        );
        System.out.println("互斥锁查询结果: " + shopMutex);

        // 2. 测试防雪崩查询 (TTL随机扰动打散 + 容灾降级)
        Shop shopAvalanche = client.queryWithAvalanchePrevention(
                RedisConstants.CACHE_SHOP_KEY, 1L, Shop.class,
                shopService::getById, RedisConstants.CACHE_SHOP_TTL, 300L, TimeUnit.SECONDS
        );
        System.out.println("防雪崩查询结果: " + shopAvalanche);
    }

    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task=()->{
            for (int i = 0; i < 100; i++) {
                Long id = redisIdWorker.nextId("order");
                System.out.println("id = "+id);
            }
            latch.countDown();
        };
        long begin=System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();

        long end=System.currentTimeMillis();
        System.out.println("time："+(end-begin));
    }
    //导入redisgeo店铺数据
    @Test
    void loadShopDate(){
        //1.查询店铺信息
        List<Shop> list = shopService.list();

        //2.把店铺分组，按照typeId分组，id一致的放到一个集合
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //3.分批完成写入redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            //获取类型id
            Long typeId = entry.getKey();
            //获取同类型店铺集合
            List<Shop>  value = entry.getValue();

            String key=SHOP_GEO_KEY+typeId;
            List<RedisGeoCommands.GeoLocation<String>> locations=new ArrayList<>();
            for (Shop shop : value) {
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(),shop.getY())
                ));
            }
            //写入redis
            stringRedisTemplate.opsForGeo().add(key,locations);
        }
    }

}


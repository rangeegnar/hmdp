package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import com.hmdp.constant.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.hmdp.constant.RedisConstants.*;


@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient clientClient;

    @Override
    public Result queryById(Long id) {
        // =========================================================================
        // 压测与业务方案切换入口（取消对应行的注释即可进行特定压测）
        // =========================================================================

        // 【基线对照组】：不使用任何缓存，直接查询 MySQL 数据库（用于压测对比基线）
        // Shop shop = getById(id);

        // 【缓存穿透方案 1】：缓存空对象（""）+ 2分钟短 TTL（首次查库回写缓存，保证可查到数据）
        // Shop shop = clientClient.queryWithPassThrough(
        //         CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 【缓存穿透方案 2】：Redisson 分布式布隆过滤器拦截 + 空值兜底
        Shop shop = clientClient.queryWithPassThroughBloom(
                 CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES, BLOOM_SHOP_KEY);

        // 【缓存击穿方案 1】：分布式互斥锁 + 自旋重试 + Double Check（CP 强一致性）
        // Shop shop = clientClient.queryWithMutex(
        //         CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 【缓存击穿方案 2】：逻辑过期时间 + 独立线程池异步重建（AP 高吞吐毫秒级响应，需提前预热）
        // Shop shop = clientClient.queryWithLogicalExpire(
        //         CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 【缓存雪崩方案 1】：TTL 随机 Jitter 扰动打散 + Redis 宕机容灾降级
        // Shop shop = clientClient.queryWithAvalanchePrevention(
        //         CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, 300L, TimeUnit.SECONDS);

        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }

    public void saveShop2Redis(Long id, Long expireSeconds) {
        // 1.查询店铺数据
        Shop shop = getById(id);
        // 2.封装成逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 3.写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 1.先修改数据库
        updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 1.是否根据坐标查询
        if (x == null || y == null) {
            // 不需要坐标查询，走数据库查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        // 2.计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // 3.查询redis，按照距离排序，分页。结果：shopId,distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        // 4.解析出id
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            distanceMap.put(shopIdStr, result.getDistance());
        });
        // 5.根据id查询shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query()
                .in("id", ids).last("order by field(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        // 6.返回
        return Result.ok(shops);
    }
}

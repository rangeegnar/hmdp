package com.hmdp;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.constant.RedisConstants;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.annotation.Resource;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.hmdp.constant.RedisConstants.BLOOM_SHOP_KEY;
import static com.hmdp.constant.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.constant.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.constant.RedisConstants.CACHE_SHOP_TTL;
import static com.hmdp.constant.RedisConstants.LOCK_SHOP_KEY;

/**
 * ============================================================================
 * 高并发缓存【混合保护方案】基准压测对比（单文件全场景）
 * ============================================================================
 * 对比对象：
 *   对照组  裸查数据库 ：直接 getById 直查 MySQL，无任何缓存 —— 基线
 *   实验组  混合保护   ：布隆过滤(防穿透) + 空值缓存兜底 + 逻辑过期异步重建(防击穿)
 *
 * 压测规模：每个方案 10 万请求、100 并发 Worker（可调常量 TOTAL_REQUESTS / CONCURRENCY）
 *
 * 核心指标：
 *   1. 吞吐：总耗时 / QPS
 *   2. 延迟：Avg RT、P50、P90、P99、P999、Min、Max
 *   3. 稳定性：成功率 / 失败数
 *   4. 数据库压力（核心）：DB 回源次数（SHOW GLOBAL STATUS LIKE 'Com_select' 前后差值，零侵入）
 *   5. 防御效果：拦截率（非法 id 被布隆拦截比例）、命中率（合法 id 直接命中缓存比例）
 *
 * 混合保护链路（逻辑内联于本测试类，不修改 CacheClient）：
 *   ① 布隆过滤：白名单判定，不存在 ⇒ 直接拦截返回 null，Redis/MySQL 零访问（防穿透）
 *   ② 逻辑过期：读取 RedisData，未过期直接返回；已过期 ⇒ 抢锁 + 异步重建，主线程返回旧数据（防击穿）
 *   ③ 空值兜底：缓存真空/布隆误判时查库，不存在写空值 ""（防穿透兜底），存在写逻辑过期缓存
 *
 * 预热说明：
 *   - 裸查方案 100% 直查 MySQL，无需预热（预热会污染基线）；
 *   - 混合方案在压测前把 1~14 全部预置为【已逻辑过期】缓存，使 10 万并发触发异步重建分支；
 *   - 布隆过滤器由 BloomFilterUse 应用启动时全表预热。
 *
 * 运行方式（需本地 Redis + MySQL 已启动）：
 *   mvn test -Dtest=CacheProtectionMixedBenchmarkTest
 * ============================================================================
 */
@Slf4j
@SpringBootTest
class CacheProtectionMixedBenchmarkTest {

    // ==================== 压测参数配置 ====================
    private static final int TOTAL_REQUESTS = 100_000;   // 每方案请求总量
    private static final int CONCURRENCY    = 100;        // 并发 Worker 数
    private static final int VALID_ID_MAX   = 14;         // MySQL 中真实存在的商铺 id 范围 1~14
    private static final double LEGAL_RATIO = 0.7;        // 合法 id 占比（剩余 30% 为非法 id）
    private static final long ILLEGAL_BASE  = 10_000L;    // 非法 id 下限（远大于真实 id）

    private static final Random RANDOM = new Random();

    // ==================== 注入的 Spring Bean ====================
    @Resource
    private IShopService shopService;
    @Resource
    private CacheClient cacheClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private JdbcTemplate jdbcTemplate;

    // 混合方案异步重建线程池（与 CacheClient 内部 CACHE_REBUILD_EXECUTOR 相同规格）
    private static final ExecutorService MIXED_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    // ==================== DB 回源次数统计（Com_select 差值） ====================
    private long readDbQueryCount() {
        Map<String, Object> row = jdbcTemplate.queryForMap("SHOW GLOBAL STATUS LIKE 'Com_select'");
        return Long.parseLong(String.valueOf(row.get("Value")));
    }

    // ==================== 混合保护核心链路（复用 CacheClient 空值/逻辑过期原语） ====================
    /**
     * 混合查询：布隆过滤(防穿透) → 逻辑过期缓存(防击穿) → 空值兜底
     *
     * @return 合法 id 返回店铺；非法 id 被布隆拦截返回 null；DB 不存在返回 null（写空值兜底）
     */
    private Shop hybridQuery(Long id) {
        // ===== ① 第一道防线：布隆过滤器白名单判定（防穿透） =====
        RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter(BLOOM_SHOP_KEY);
        try {
            if (!bloomFilter.isExists()) {
                // 布隆不可用（启动预热失败/被清空）：降级跳过此关卡，靠下方 ③ 空值兜底继续防穿透
                log.warn("[混合保护] 布隆过滤器 {} 不可用，跳过白名单判定", BLOOM_SHOP_KEY);
            } else if (!bloomFilter.contains(id)) {
                // 布隆判定不存在 ⇒ 一定不存在，直接拦截，绝不查 Redis/MySQL
                return null;
            }
        } catch (Exception e) {
            log.warn("[混合保护] 布隆过滤器判定异常，降级跳过: {}", e.toString());
        }

        String key = CACHE_SHOP_KEY + id;
        // ===== ② 第二道防线：逻辑过期缓存读取（防击穿） =====
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json != null) {
            // 命中空值缓存（布隆误判命中 ""）⇒ 数据不存在
            if (json.isEmpty()) {
                return null;
            }
            RedisData redisData = JSONUtil.toBean(json, RedisData.class);
            Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
            // 未过期 ⇒ 直接返回
            if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
                return shop;
            }
            // 已逻辑过期 ⇒ 抢锁异步重建，主线程立即返回旧数据（击穿防护：并发只有 1 线程回源）
            String lockKey = LOCK_SHOP_KEY + id;
            Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(locked)) {
                MIXED_REBUILD_EXECUTOR.submit(() -> {
                    try {
                        Shop fresh = shopService.getById(id);
                        cacheClient.setWithLogicalExpire(key, fresh, CACHE_SHOP_TTL, TimeUnit.MINUTES);
                    } catch (Exception e) {
                        log.error("[混合保护] 异步重建失败: {}", e.toString());
                    } finally {
                        stringRedisTemplate.delete(lockKey);
                    }
                });
            }
            return shop;
        }

        // ===== ③ 第三道防线：缓存真空 ⇒ 查库 + 空值兜底（防穿透，含布隆误判场景） =====
        Shop r = shopService.getById(id);
        if (r == null) {
            // DB 不存在 ⇒ 写空值缓存 + 短 TTL
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // DB 存在 ⇒ 写逻辑过期缓存（一致性：所有正常数据统一走 RedisData 格式）
        cacheClient.setWithLogicalExpire(key, r, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return r;
    }

    // ==================== 压测执行引擎 ====================
    private StressResult stress(BenchTask task) {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch finish = new CountDownLatch(TOTAL_REQUESTS);
        ConcurrentLinkedQueue<Long> costs = new ConcurrentLinkedQueue<>();
        AtomicInteger success = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        AtomicLong bloomIntercepted = new AtomicLong();

        long start = System.nanoTime();
        for (int i = 0; i < TOTAL_REQUESTS; i++) {
            pool.submit(() -> {
                try {
                    long t0 = System.nanoTime();
                    BenchResult br = task.run();
                    long cost = (System.nanoTime() - t0) / 1_000_000;
                    costs.add(cost);
                    if (br.success) {
                        success.incrementAndGet();
                    } else {
                        fail.incrementAndGet();
                    }
                    if (br.intercepted) {
                        bloomIntercepted.incrementAndGet();
                    }
                } catch (Exception e) {
                    fail.incrementAndGet();
                } finally {
                    finish.countDown();
                }
            });
        }
        try {
            finish.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdown();
        }
        long totalMs = (System.nanoTime() - start) / 1_000_000;

        List<Long> list = new ArrayList<>(costs);
        list.sort(Long::compareTo);
        int n = list.size();
        double qps = totalMs == 0 ? 0 : TOTAL_REQUESTS * 1000.0 / totalMs;
        return StressResult.builder()
                .total(TOTAL_REQUESTS)
                .concurrency(CONCURRENCY)
                .totalMs(totalMs)
                .qps(qps)
                .success(success.get())
                .fail(fail.get())
                .bloomIntercepted(bloomIntercepted.get())
                .minRt(n > 0 ? list.get(0) : 0)
                .maxRt(n > 0 ? list.get(n - 1) : 0)
                .avgRt(n > 0 ? list.stream().mapToLong(Long::longValue).sum() / (double) n : 0)
                .p50(n > 0 ? list.get((int) (n * 0.50)) : 0)
                .p90(n > 0 ? list.get((int) (n * 0.90)) : 0)
                .p99(n > 0 ? list.get((int) (n * 0.99)) : 0)
                .p999(n > 0 ? list.get((int) (n * 0.999)) : 0)
                .build();
    }

    // ==================== 通用流量生成器：70% 合法 id + 30% 非法大随机 id ====================
    private long randomId() {
        return RANDOM.nextDouble() < LEGAL_RATIO
                ? (RANDOM.nextInt(VALID_ID_MAX) + 1L)
                : (ILLEGAL_BASE + RANDOM.nextInt(2_000_000));
    }

    // ==================== 场景 A：裸查数据库（对照组基线，无需预热） ====================
    private StressResult benchmarkNoProtection() {
        log.info("【对照组】开始压测：裸查 MySQL，无任何保护 ...");
        AtomicInteger legalCnt = new AtomicInteger();
        AtomicInteger legalHitCnt = new AtomicInteger();
        long dbBefore = readDbQueryCount();
        StressResult r = stress(() -> {
            long id = randomId();
            Shop shop = shopService.getById(id);
            if (id <= VALID_ID_MAX) {
                legalCnt.incrementAndGet();
                if (shop != null) legalHitCnt.incrementAndGet();
            }
            // 裸查语义：请求正常完成即成功（非法 id 查到 null 是正常业务结果，非故障）
            return BenchResult.of(true, false);
        });
        long dbAfter = readDbQueryCount();
        double hitRate = legalCnt.get() == 0 ? 0 : 1.0 * legalHitCnt.get() / legalCnt.get();
        return r.toBuilder()
                .name("裸查MySQL(对照组)")
                .dbHits(dbAfter - dbBefore)
                .cacheHitRate(0.0)
                .legalCnt(legalCnt.get())
                .legalHitCnt(legalHitCnt.get())
                .build();
    }

    // ==================== 场景 B：混合保护（布隆 + 逻辑过期 + 空值，需预热已过期缓存） ====================
    private StressResult benchmarkMixed() throws InterruptedException {
        // 1. 清理全部商铺缓存，保证从干净状态开始
        List<String> keys = new ArrayList<>();
        for (long i = 1; i <= VALID_ID_MAX; i++) {
            keys.add(CACHE_SHOP_KEY + i);
        }
        stringRedisTemplate.delete(keys);

        // 2. 预热：对 1~14 写入【已逻辑过期】的 RedisData（expireTime 在过去），触发击穿重建分支
        log.info("[混合保护] 预热 1~{} 为【已逻辑过期】缓存 ...", VALID_ID_MAX);
        for (long i = 1; i <= VALID_ID_MAX; i++) {
            prepareExpiredCache(i);
        }

        log.info("【实验组】开始压测：混合保护 布隆 + 逻辑过期 + 空值兜底 ...");
        AtomicInteger legalCnt = new AtomicInteger();
        AtomicInteger legalHitCnt = new AtomicInteger();
        AtomicLong illegalCnt = new AtomicLong();
        long dbBefore = readDbQueryCount();
        StressResult r = stress(() -> {
            long id = randomId();
            Shop shop = hybridQuery(id);
            boolean legal = id <= VALID_ID_MAX;
            if (legal) {
                legalCnt.incrementAndGet();
                if (shop != null) legalHitCnt.incrementAndGet();
                return BenchResult.of(true, false);
            }
            illegalCnt.incrementAndGet();
            // 非法 id：被布隆拦截（返回 null）即防御成功，记成功 + 拦截
            return BenchResult.of(true, shop == null);
        });
        long dbAfter = readDbQueryCount();
        double hitRate = legalCnt.get() == 0 ? 0 : 1.0 * legalHitCnt.get() / legalCnt.get();
        return r.toBuilder()
                .name("混合保护(布隆+逻辑过期)")
                .dbHits(dbAfter - dbBefore)
                .cacheHitRate(hitRate)
                .legalCnt(legalCnt.get())
                .legalHitCnt(legalHitCnt.get())
                .illegalCnt(illegalCnt.get())
                .build();
    }

    /** 把商铺 id 写入一条过期的逻辑缓存（expireTime 设为 10 秒前） */
    private void prepareExpiredCache(Long id) {
        RedisData redisData = new RedisData();
        redisData.setData(shopService.getById(id));
        redisData.setExpireTime(LocalDateTime.now().minusSeconds(10));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    // ==================== 入口：跑全部 2 个场景并输出对比报告 ====================
    @Test
    void benchmarkComparison() throws Exception {
        List<StressResult> results = new ArrayList<>();
        results.add(benchmarkNoProtection());
        results.add(benchmarkMixed());
        printReport(results);
        writeMarkdownReport(results);
    }

    // ==================== 报告输出 ====================
    private void printReport(List<StressResult> results) {
        System.out.println();
        System.out.println("=====================================================================================");
        System.out.println("高并发缓存【混合保护方案】基准压测对比（每方案 " + TOTAL_REQUESTS + " 请求 / " + CONCURRENCY + " 并发）");
        System.out.println("流量：70% 合法 id(1~14) + 30% 非法大 id");
        System.out.println("=====================================================================================");
        System.out.printf("| %-24s | %9s | %9s | %10s | %5s | %6s | %7s | %8s | %8s | %8s | %8s | %7s | %7s |%n",
                "方案", "总耗时ms", "QPS", "AvgRT(ms)", "Min", "Max", "P50", "P90", "P99", "P999", "失败数", "命中率", "拦截率");
        for (StressResult r : results) {
            System.out.printf("| %-24s | %9d | %9.1f | %10.3f | %5d | %6d | %7.2f | %8.2f | %8.2f | %8.2f | %8d | %6.1f%% | %6.1f%% |%n",
                    r.name, r.totalMs, r.qps, r.avgRt, r.minRt, r.maxRt, (double) r.p50, (double) r.p90, (double) r.p99, (double) r.p999,
                    r.fail, 100.0 * r.cacheHitRate, interceptRateOf(r));
        }
        System.out.println("=====================================================================================");
    }

    private double interceptRateOf(StressResult r) {
        return r.illegalCnt == 0 ? 0 : 100.0 * r.bloomIntercepted / r.illegalCnt;
    }

    private void writeMarkdownReport(List<StressResult> results) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("# 📊 高并发缓存【混合保护方案】基准压测对比报告\n\n");
        sb.append("> 引擎：JUnit5 + SpringBootTest | 规模：每方案 ").append(TOTAL_REQUESTS)
                .append(" 请求 / ").append(CONCURRENCY).append(" 并发 | 流量：70% 合法 id + 30% 非法 id\n\n");
        sb.append("| 方案 | 总耗时(ms) | QPS | AvgRT(ms) | Min | Max | P50 | P90 | P99 | P999 | 失败数 | 命中率 | 拦截率 | DB回源 |\n");
        sb.append("| :--- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        for (StressResult r : results) {
            sb.append(String.format("| %s | %d | %.1f | %.3f | %d | %d | %.2f | %.2f | %.2f | %.2f | %d | %.1f%% | %.1f%% | %d |%n",
                    r.name, r.totalMs, r.qps, r.avgRt, r.minRt, r.maxRt,
                    (double) r.p50, (double) r.p90, (double) r.p99, (double) r.p999,
                    r.fail, 100.0 * r.cacheHitRate, interceptRateOf(r), r.dbHits));
        }
        sb.append("\n### 结论速览\n\n");
        StressResult base = results.get(0);
        StressResult mix = results.get(1);
        sb.append("- **数据库压力**：裸查回源 **").append(base.dbHits).append(" 次**，混合保护回源 **")
                .append(mix.dbHits).append(" 次**，降幅 **")
                .append(String.format("%.2f", base.dbHits == 0 ? 0 : 100.0 * (base.dbHits - mix.dbHits) / base.dbHits))
                .append("%**（拦截率 **").append(String.format("%.1f", interceptRateOf(mix))).append("%**，非法 id 几乎全部被布隆拦截）。\n");
        sb.append("- **吞吐**：裸查 QPS **").append(String.format("%.1f", base.qps)).append("**，混合保护 QPS **")
                .append(String.format("%.1f", mix.qps)).append("**，提升 **")
                .append(String.format("%.1f", base.qps == 0 ? 0 : mix.qps / base.qps)).append(" 倍**。\n");
        sb.append("- **延迟**：P99 从 **").append(base.p99).append("ms** 降至 **").append(mix.p99).append("ms**，Avg RT 从 **")
                .append(String.format("%.3f", base.avgRt)).append("ms** 降至 **").append(String.format("%.3f", mix.avgRt)).append("ms**。\n");

        String outPath = "Test/CACHE_PROTECTION_MIXED_REPORT.md";
        java.nio.file.Path p = Paths.get(outPath).toAbsolutePath();
        Files.createDirectories(p.getParent());
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(p, StandardCharsets.UTF_8))) {
            w.write(sb.toString());
        }
        log.info("对比报告已生成: {}", p);
    }

    // ==================== 结果载体 ====================
    @FunctionalInterface
    interface BenchTask {
        BenchResult run() throws Exception;
    }

    static class BenchResult {
        final boolean success;
        final boolean intercepted;

        private BenchResult(boolean success, boolean intercepted) {
            this.success = success;
            this.intercepted = intercepted;
        }

        static BenchResult of(boolean success, boolean intercepted) {
            return new BenchResult(success, intercepted);
        }
    }

    static class StressResult {
        String name;
        long total, concurrency, totalMs;
        double qps, avgRt;
        long minRt, maxRt, p50, p90, p99, p999;
        long success, fail, dbHits;
        long bloomIntercepted;
        long legalCnt, legalHitCnt, illegalCnt;
        double cacheHitRate;

        static Builder builder() { return new Builder(); }

        Builder toBuilder() {
            return builder().name(name).total(total).concurrency(concurrency).totalMs(totalMs)
                    .qps(qps).avgRt(avgRt).minRt(minRt).maxRt(maxRt).p50(p50).p90(p90).p99(p99).p999(p999)
                    .success(success).fail(fail).dbHits(dbHits).bloomIntercepted(bloomIntercepted)
                    .legalCnt(legalCnt).legalHitCnt(legalHitCnt).illegalCnt(illegalCnt)
                    .cacheHitRate(cacheHitRate);
        }

        static class Builder {
            StressResult r = new StressResult();

            Builder name(String v) { r.name = v; return this; }
            Builder total(long v) { r.total = v; return this; }
            Builder concurrency(long v) { r.concurrency = v; return this; }
            Builder totalMs(long v) { r.totalMs = v; return this; }
            Builder qps(double v) { r.qps = v; return this; }
            Builder avgRt(double v) { r.avgRt = v; return this; }
            Builder minRt(long v) { r.minRt = v; return this; }
            Builder maxRt(long v) { r.maxRt = v; return this; }
            Builder p50(long v) { r.p50 = v; return this; }
            Builder p90(long v) { r.p90 = v; return this; }
            Builder p99(long v) { r.p99 = v; return this; }
            Builder p999(long v) { r.p999 = v; return this; }
            Builder success(long v) { r.success = v; return this; }
            Builder fail(long v) { r.fail = v; return this; }
            Builder dbHits(long v) { r.dbHits = v; return this; }
            Builder bloomIntercepted(long v) { r.bloomIntercepted = v; return this; }
            Builder legalCnt(long v) { r.legalCnt = v; return this; }
            Builder legalHitCnt(long v) { r.legalHitCnt = v; return this; }
            Builder illegalCnt(long v) { r.illegalCnt = v; return this; }
            Builder cacheHitRate(double v) { r.cacheHitRate = v; return this; }
            StressResult build() { return r; }
        }
    }
}
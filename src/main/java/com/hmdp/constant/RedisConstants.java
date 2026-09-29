package com.hmdp.constant;

public class RedisConstants {
    // 登录相关
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 300000L;
    public static final String LOGIN_USER_TOKENS_KEY = "login:user:tokens:";
    public static final Long LOGIN_USER_TOKENS_TTL = 7L;
    public static final int MAX_ONLINE_DEVICES = 3;

    // 缓存穿透，击穿，雪崩相关
    public static final Long CACHE_NULL_TTL = 2L;

    public static final String BLOOM_SHOP_KEY = "bloom:shop";

    // 分布式锁相关
    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";

    // 其他业务功能
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String FOLLOW_KEY = "follows:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";

    public static final String SHOP_TYPE_KEY = "shop_type:";
    public static final Long SHOP_TYPE_LONG = 10L;
}

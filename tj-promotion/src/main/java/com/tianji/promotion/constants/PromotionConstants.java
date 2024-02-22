package com.tianji.promotion.constants;

public class PromotionConstants {
    /**
     * 全局公用的 redis递增序列号，每生成一个兑换码，序列号自增 1
     */
    public final static String COUPON_CODE_SERIAL_KEY = "coupon:code:serial";

    /**
     * 校验兑换码是否使用过的 key
     */
    public final static String COUPON_CODE_MAP_KEY = "coupon:code:map";

    /**
     * 缓存的优惠券信息 key前缀
     */
    public final static String COUPON_CACHE_KEY_PREFIX = "prs:coupon:";

    /**
     * 缓存的用户券信息 key前缀
     */
    public final static String USER_COUPON_CACHE_KEY_PREFIX = "prs:user:coupon:";

    /**
     * 兑换码序列号，便于找到对应优惠券 id
     */
    public final static String COUPON_RANGE_KEY = "coupon:code:range";
}

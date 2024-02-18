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
}

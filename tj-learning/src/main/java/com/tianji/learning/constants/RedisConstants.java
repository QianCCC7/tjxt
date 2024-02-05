package com.tianji.learning.constants;

public interface RedisConstants {
    /**
     * 签到记录的 key的前缀，sign:uid:{}:{} 第一个参数为 uid，第二个为签到日期
     */
    String SIGN_RECORD_KEY_PREFIX = "sign:uid:";

    /**
     * 积分排行榜的 key的前缀，boards:{} 参数为当前赛季的日期
     */
    String POINTS_BOARD_KEY_PREFIX = "boards:";
}

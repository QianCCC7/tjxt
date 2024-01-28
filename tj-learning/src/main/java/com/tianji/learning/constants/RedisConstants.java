package com.tianji.learning.constants;

public interface RedisConstants {
    /**
     * 签到记录的 key的前缀，sign:uid:{}:{} 第一个参数为 uid，第二个为签到日期
     */
    String SIGN_RECORD_KEY_PREFIX = "sign:uid:";
}

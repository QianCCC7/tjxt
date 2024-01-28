package com.tianji.learning.service.impl;

import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.service.ISignRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SignRecordServiceImpl implements ISignRecordService {

    private final StringRedisTemplate redisTemplate;

    /**
     * 保存签到记录
     */
    @Override
    public SignResultVO addSignRecords() {
        // 1. 获取当前登录用户
        Long userId = UserContext.getUser();
        // 2. 签到
        // 2.1 获取当前日期
        LocalDate now = LocalDate.now();
        // 2.2 拼接 key
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX
                + userId.toString()
                + ":"
                + now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        // 2.3 计算 offset
        long offset = now.getDayOfMonth() - 1;
        Boolean exits = redisTemplate.opsForValue().setBit(key, offset, true);// true代表 1
        if (Boolean.TRUE.equals(exits)) {
            throw new BizIllegalException("不允许重复签到!");
        }
        // 3. 计算本月连续签到天数
        int signDays = countSignDays(key, now.getDayOfMonth());
        // 4. TODO 计算签到得分

        // 5. TODO 保存积分明细记录

        // 6. 封装返回
        SignResultVO vo = new SignResultVO();
        vo.setSignDays(signDays);
        vo.setRewardPoints(0);
        vo.setSignPoints(0);
        return vo;
    }
    /**
     * 计算用户本月连续签到天数
     */
    private int countSignDays(String key, int dayOfMonth) {
        // 1. 获取本月从第一天开始到今天为止的所有签到记录
        List<Long> list = redisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if (CollUtils.isEmpty(list)) return 0;
        int num = list.get(0).intValue();// 签到记录返回的十进制数字
        // 2. 计算连续签到天数
        int cnt = 0;
        while ((num & 1) == 1) {
            cnt++;
            num >>>= 1;// 无符号右移
        }
        return cnt;
    }
}

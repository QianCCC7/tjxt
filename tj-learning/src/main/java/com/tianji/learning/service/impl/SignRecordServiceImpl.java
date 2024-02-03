package com.tianji.learning.service.impl;

import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.mq.message.SignInMessage;
import lombok.RequiredArgsConstructor;
import com.tianji.learning.service.ISignRecordService;
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
    private final RabbitMqHelper mqHelper;

    /**
     * 用户签到时，给予积分奖励并保存签到记录
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
        // 4. 计算签到可以加的积分，连续签到有额外积分
        int rewardPoints = 0;
        switch (signDays) {
            case 7:
                rewardPoints = 10;
                break;
            case 14:
                rewardPoints = 20;
                break;
            case 28:
                rewardPoints = 40;
                break;
        }
        // 5. MQ通知保存积分明细记录
        mqHelper.send(
                MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.SIGN_IN,
                SignInMessage.of(userId, rewardPoints + 1)
        );
        // 6. 封装返回
        SignResultVO vo = new SignResultVO();
        vo.setSignDays(signDays);
        vo.setRewardPoints(rewardPoints);
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

    /**
     * 查询本月签到记录
     */
    @Override
    public Byte[] querySignRecords() {
        // 1. 获取登录用户
        Long userId = UserContext.getUser();
        // 2. 获取当前日期
        LocalDate now = LocalDate.now();
        // 3. 拼接 key
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX
                + userId
                + ":"
                + now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        int dayOfMonth = now.getDayOfMonth();
        // 4. 查询本月从第一天开始到今天为止的所有签到记录的十进制数
        List<Long> list = redisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if (CollUtils.isEmpty(list)) return new Byte[0];
        int num = list.get(0).intValue();
        Byte[] times = new Byte[dayOfMonth];
        // 从当前天开始枚举(注意二进制左边是第一天，最右边才是最后一天)
        int cur = dayOfMonth - 1;
        while (num != 0) {
            times[cur] = (byte) (num & 1);
            num >>>= 1;
            cur--;
        }
        return times;
    }
}

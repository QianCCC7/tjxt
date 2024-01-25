package com.tianji.remark.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.remark.LikeTimesDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.constants.RedisConstants;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.pojo.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tianji.common.constants.MqConstants.Exchange.LIKE_RECORD_EXCHANGE;
import static com.tianji.common.constants.MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE;

/**
 * <p>
 * 点赞记录表 服务实现类 Redis版本
 * </p>
 *
 * @author QianCCC
 * @since 2024-01-23
 */
@Service
@RequiredArgsConstructor
public class LikedRecordServiceRedisImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {

    private final RabbitMqHelper mqHelper;
    private final StringRedisTemplate redisTemplate;

    /**
     * 用户点赞或者取消点赞
     */
    @Override
    public void addLikeRecord(LikeRecordFormDTO recordFormDTO) {
        Long userId = UserContext.getUser();
        // 1. 根据 recordFormDTO中的 liked字段，判断执行的是点赞业务还是取消点赞业务
        boolean success = recordFormDTO.getLiked() ? liked(recordFormDTO, userId) : unliked(recordFormDTO, userId);
        // 2. 判断是否执行成功，如果失败，直接结束
        if (!success) return;
        // 3. 执行成功，统计该问答或频率的点赞总数
        String key = RedisConstants.LIKES_BIZ_KEY_PREFIX + recordFormDTO.getBizId();
        Long likedTimes = redisTemplate.opsForSet().size(key);
        if (Objects.isNull(likedTimes)) {
            return;
        }
        // 4. 缓存点赞总数到 redis，即记录各个业务的点赞数量的ZSet中
        String zSetKey = RedisConstants.LIKES_TIMES_KEY_PREFIX + recordFormDTO.getBizType();
        redisTemplate.opsForZSet().add(zSetKey, recordFormDTO.getBizId().toString(), likedTimes);
    }

    /**
     * 点赞
     */
    private boolean liked(LikeRecordFormDTO recordFormDTO, Long userId) {
        // 1. 获取 key
        String key = RedisConstants.LIKES_BIZ_KEY_PREFIX + recordFormDTO.getBizId();
        // 2. 执行SADD命令
        Long res = redisTemplate.opsForSet().add(key, userId.toString()); // 注意用户 id要转为 String
        return Objects.nonNull(res) && res > 0;
    }

    /**
     * 取消点赞
     */
    private boolean unliked(LikeRecordFormDTO recordFormDTO, Long userId) {
        // 1. 获取 key
        String key = RedisConstants.LIKES_BIZ_KEY_PREFIX + recordFormDTO.getBizId();
        // 2. 执行SREM命令
        Long res = redisTemplate.opsForSet().remove(key, userId.toString()); // 注意用户 id要转为 String
        return Objects.nonNull(res) && res > 0;
    }

    /**
     * 查询业务 id集合中每个的点赞状态
     */
    @Override
    public Set<Long> isBizLiked(List<Long> bizIds) {
        // 1. 获取登录用户
        Long userId = UserContext.getUser();
        // 2. 查询点赞状态
        List<LikedRecord> list = lambdaQuery()
                .eq(LikedRecord::getUserId, userId)
                .in(LikedRecord::getBizId, bizIds)
                .list();
        // 3. 返回结果
        if (CollUtils.isNotEmpty(list)) {
            return list.stream().map(LikedRecord::getBizId).collect(Collectors.toSet());
        }
        return null;
    }
}

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
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
        // 通过批处理，这样可以在一次查询业务 id集合中每个的点赞状态请求中,redis可以一次批量处理 bizIds.size()次 isMember命令
        // 并通过返回一次查询结果，如果是原本方式，那么需要返回 bizIds.size()次 isMember的结果
        List<Object> list = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection src = (StringRedisConnection) connection;// 强转为 StringRedisConnection
            for (Long bizId : bizIds) {
                String key = RedisConstants.LIKES_BIZ_KEY_PREFIX + bizId;
                src.sIsMember(key, userId.toString());// 注意结果会自动封装到自己原本的坐标中，1->true, 2->false
            }
            return null;// 不需要关注返回结果
        });
        // 3. 返回结果
        // for (int i = 0; i < list.size(); i++) {
        //     Boolean o = (Boolean) list.get(i);// 这个就是对应坐标 src.sIsMember(key, userId.toString());的值
        //     if (o) {
        //         set.add(bizIds.get(i));
        //     }
        // }
        Set<Long> set = IntStream
                .range(0, list.size()) // 范围为 0 ~ list.size()
                .filter(i -> (boolean) list.get(i)) // 过滤出所有点赞状态为 true的 bizId的坐标
                .mapToObj(i -> bizIds.get(i)) // 获取对应坐标出的 bizId
                .collect(Collectors.toSet());// 转为 set
        if (CollUtils.isNotEmpty(set)) {
            return set;
        }
        return null;
    }

    /**
     * 根据业务类型，统计其对应业务的点赞数量，然后通过 mq消息通知监听业务区更新点赞数量
     */
    @Override
    public void readLikedTimesAndSendMessage(String bizType, int maxBizSize) {
        // 1. 读取并移除 Redis中缓存的点赞总数
        // 建议从小到大读取数据，因为 score较大的数据对点赞没有 score较小的数据敏感
        String key = RedisConstants.LIKES_TIMES_KEY_PREFIX + bizType;
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().popMin(key, maxBizSize);
        if (CollUtils.isEmpty(tuples)) return;
        // 2. 数据转换
        List<LikeTimesDTO> list = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String bizId = tuple.getValue();
            Double likedTimes = tuple.getScore();
            if (Objects.isNull(bizId) || Objects.isNull(likedTimes)) {
                continue;
            }
            list.add(LikeTimesDTO.of(Long.valueOf(bizId), likedTimes.intValue()));
        }
        // 3. 发送MQ
        mqHelper.send(
                LIKE_RECORD_EXCHANGE,
                StringUtils.format(LIKED_TIMES_KEY_TEMPLATE, bizType),
                list
        );
    }
}

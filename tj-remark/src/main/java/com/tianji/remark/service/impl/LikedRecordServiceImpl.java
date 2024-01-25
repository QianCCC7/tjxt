package com.tianji.remark.service.impl;

import com.tianji.api.remark.LikeTimesDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.pojo.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tianji.common.constants.MqConstants.Exchange.LIKE_RECORD_EXCHANGE;
import static com.tianji.common.constants.MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE;

/**
 * <p>
 * 点赞记录表 服务实现类
 * </p>
 *
 * @author QianCCC
 * @since 2024-01-23
 */
// @Service
@RequiredArgsConstructor
public class LikedRecordServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {

    private final RabbitMqHelper mqHelper;

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
        // 3. 执行成功，统计点赞总数
        Integer likedTimes = lambdaQuery()
                .eq(LikedRecord::getBizId, recordFormDTO.getBizId())
                .count();
        // 4. 发送MQ通知
        mqHelper.send(
                LIKE_RECORD_EXCHANGE,
                StringUtils.format(LIKED_TIMES_KEY_TEMPLATE, recordFormDTO.getBizType()),
                LikeTimesDTO.of(recordFormDTO.getBizId(), likedTimes)
        );
    }

    /**
     * 点赞
     */
    private boolean liked(LikeRecordFormDTO recordFormDTO, Long userId) {
        // 1. 查询点赞记录
        Integer count = lambdaQuery()
                .eq(LikedRecord::getUserId, userId)
                .eq(LikedRecord::getBizId, recordFormDTO.getBizId())
                .count();
        // 2. 如果存在，直接结束
        if (count > 0) {
            return false;
        }
        // 3. 如果不存在，新增记录
        LikedRecord likedRecord = BeanUtils.copyBean(recordFormDTO, LikedRecord.class);
        likedRecord.setUserId(userId);
        return save(likedRecord);
    }

    /**
     * 取消点赞
     */
    private boolean unliked(LikeRecordFormDTO recordFormDTO, Long userId) {
        // 1. 查询点赞记录
        LikedRecord likedRecord = lambdaQuery()
                .eq(LikedRecord::getUserId, userId)
                .eq(LikedRecord::getBizId, recordFormDTO.getBizId())
                .one();
        // 2. 如果不存在，直接结束
        if (Objects.isNull(likedRecord)) {
            return false;
        }
        // 3. 如果存在，删除记录
        return removeById(likedRecord.getId());
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

    @Override
    public void readLikedTimesAndSendMessage(String bizType, int maxBizSize) {

    }
}

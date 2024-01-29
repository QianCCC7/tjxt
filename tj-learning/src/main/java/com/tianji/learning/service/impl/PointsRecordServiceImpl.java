package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianji.common.utils.DateUtils;
import com.tianji.learning.domain.pojo.PointsRecord;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mapper.PointsRecordMapper;
import com.tianji.learning.service.IPointsRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务实现类
 * </p>
 *
 * @author QianCCC
 * @since 2024-01-28
 */
@Service
public class PointsRecordServiceImpl extends ServiceImpl<PointsRecordMapper, PointsRecord> implements IPointsRecordService {

    /**
     * 新增积分
     */
    public void addPointsRecord(Long userId, int points, PointsRecordType type) {
        // 1. 判断当前方式是否有积分上限
        int maxPoints = type.getMaxPoints();
        int can = points;
        // 2. 有，则判断今日积分是否超过上限
        if (maxPoints > 0) {// 大于 0则有上限
            // 2.1 查询今日获得积分
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startTime = DateUtils.getDayStartTime(now), entTime = DateUtils.getDayEndTime(now);
            int curPoints = queryPointsByTypeAndDate(userId, type, startTime, entTime);
            // 2.2 判断是否超过上限
            if (curPoints >= maxPoints) {
                // 2.3 超过，直接结束
                return;
            }
            // 2.4 未超过，计算能累加的积分
            can = Math.min(maxPoints - curPoints, points);
        }
        // 3. 无，则直接保存积分记录
        PointsRecord pointsRecord = new PointsRecord();
        pointsRecord.setUserId(userId);
        pointsRecord.setType(type);
        pointsRecord.setPoints(can);
        save(pointsRecord);
    }

    /**
     * 查询用户今日获取积分
     */
    private int queryPointsByTypeAndDate(Long userId, PointsRecordType type, LocalDateTime startTime, LocalDateTime entTime) {
        // 1. 先封装
        LambdaQueryWrapper<PointsRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper
                .eq(PointsRecord::getUserId, userId)
                .eq(Objects.nonNull(type), PointsRecord::getType, type)
                .between(Objects.nonNull(startTime) && Objects.nonNull(entTime),PointsRecord::getCreateTime, startTime, entTime);
        // 2. 调用 mapper查询结果
        Integer points = getBaseMapper().queryPointsByTypeAndDate(queryWrapper);
        // 3. 判断并返回
        return Objects.isNull(points) ? 0 : points;
    }
}

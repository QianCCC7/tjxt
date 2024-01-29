package com.tianji.learning.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.tianji.learning.domain.pojo.PointsRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 * 学习积分记录，每个月底清零 Mapper 接口
 * </p>
 *
 * @author QianCCC
 * @since 2024-01-28
 */
public interface PointsRecordMapper extends BaseMapper<PointsRecord> {

    @Select("select sum(points) from points_record ${ew.customSqlSegment}")
    Integer queryPointsByTypeAndDate(@Param(Constants.WRAPPER) LambdaQueryWrapper<PointsRecord> queryWrapper);

    @Select("select type, sum(points) as points from points_record ${ew.customSqlSegment} group by type")
    List<PointsRecord> queryPointsByDate(@Param(Constants.WRAPPER) LambdaQueryWrapper<PointsRecord> queryWrapper);
}

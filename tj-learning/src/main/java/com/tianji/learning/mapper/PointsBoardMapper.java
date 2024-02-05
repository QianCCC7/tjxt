package com.tianji.learning.mapper;

import com.tianji.learning.domain.pojo.PointsBoard;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * 学霸天梯榜 Mapper 接口
 * </p>
 *
 * @author QianCCC
 * @since 2024-01-28
 */
public interface PointsBoardMapper extends BaseMapper<PointsBoard> {

    void createTableBySeasonId(@Param("tableName") String tableName);
}

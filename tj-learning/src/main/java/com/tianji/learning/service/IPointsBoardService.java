package com.tianji.learning.service;

import com.tianji.learning.domain.pojo.PointsBoard;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardVO;

import java.util.List;

/**
 * <p>
 * 学霸天梯榜 服务类
 * </p>
 *
 * @author QianCCC
 * @since 2024-01-28
 */
public interface IPointsBoardService extends IService<PointsBoard> {

    PointsBoardVO queryPointsBoardBySeason(PointsBoardQuery query);

    List<PointsBoard> queryCurrentBoardList(String key, Integer pageNo, Integer pageSize);

    void createTableBySeasonId(Integer seasonId);
}

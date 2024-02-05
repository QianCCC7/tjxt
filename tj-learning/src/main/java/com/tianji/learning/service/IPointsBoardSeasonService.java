package com.tianji.learning.service;

import com.tianji.learning.domain.pojo.PointsBoardSeason;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.vo.PointsBoardSeasonVO;

import java.time.LocalDate;
import java.util.List;

/**
 * <p>
 * 赛季表 服务类
 * </p>
 *
 * @author QianCCC
 * @since 2024-01-28
 */
public interface IPointsBoardSeasonService extends IService<PointsBoardSeason> {

    List<PointsBoardSeasonVO> queryPointsBoardSeasons();

    Integer querySeasonIdByTime(LocalDate lastMonth);
}

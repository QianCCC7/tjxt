package com.tianji.learning.service.impl;

import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.learning.domain.pojo.PointsBoardSeason;
import com.tianji.learning.domain.vo.PointsBoardSeasonVO;
import com.tianji.learning.mapper.PointsBoardSeasonMapper;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * <p>
 * 赛季表 服务实现类
 * </p>
 *
 * @author QianCCC
 * @since 2024-01-28
 */
@Service
public class PointsBoardSeasonServiceImpl extends ServiceImpl<PointsBoardSeasonMapper, PointsBoardSeason> implements IPointsBoardSeasonService {

    /**
     * 查询历史季列表，必须是当前赛季之前的（开始时间小于等于当前时间）
     */
    @Override
    public List<PointsBoardSeasonVO> queryPointsBoardSeasons() {
        LocalDateTime now = LocalDateTime.now();
        List<PointsBoardSeason> pointsBoardSeasons = lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, now)
                .list();
        if (CollUtils.isNotEmpty(pointsBoardSeasons)) {
            return pointsBoardSeasons.stream()
                    .map(e -> BeanUtils.copyBean(e, PointsBoardSeasonVO.class))
                    .collect(Collectors.toList());
        }
        return CollUtils.emptyList();
    }

    /**
     * 根据时间查询赛季 id
     */
    @Override
    public Integer querySeasonIdByTime(LocalDate lastMonth) {
        // 注意lt和gt不要写反，时间是 beginTime <= lastMonth <= endTime
        Optional<PointsBoardSeason> optional = lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, lastMonth)
                .ge(PointsBoardSeason::getEndTime, lastMonth)
                .oneOpt();
        return optional.map(PointsBoardSeason::getId).orElse(null);
    }
}

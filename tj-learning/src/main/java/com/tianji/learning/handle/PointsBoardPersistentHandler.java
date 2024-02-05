package com.tianji.learning.handle;

import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsBoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 持久化 redis赛季榜单数据到数据库的定时任务
 */
@Component
@RequiredArgsConstructor
public class PointsBoardPersistentHandler {
    private final IPointsBoardSeasonService seasonService;
    private final IPointsBoardService boardService;


    @Scheduled(cron = "0 0 3 1 * ?")// 每月1号的凌晨3点的0分0秒处理任务，*表示每个月，？表示对周不做限制
    public void createPointsBoardTableByLastSeason() {
        // 1. 获取上个月的时间
        LocalDate lastMonth = LocalDate.now().minusMonths(1);
        // 2. 查询上个月的赛季 id
        Integer seasonId = seasonService.querySeasonIdByTime(lastMonth);
        if (seasonId == null) {
            return;// 赛季不存在
        }
        // 3. 生成新表
        boardService.createTableBySeasonId(seasonId);
    }
}

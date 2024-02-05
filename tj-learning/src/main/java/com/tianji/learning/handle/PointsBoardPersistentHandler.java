package com.tianji.learning.handle;

import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.pojo.PointsBoard;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.utils.TableInfoContext;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 持久化 redis赛季榜单数据到数据库的定时任务
 */
@Component
@RequiredArgsConstructor
public class PointsBoardPersistentHandler {
    private final IPointsBoardSeasonService seasonService;
    private final IPointsBoardService boardService;
    private final StringRedisTemplate redisTemplate;

    /**
     * 定时生成数据库表
     */
    @XxlJob("createTableJob") // 任务名称
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

    /**
     * 定时将 redis数据库持久化到上面新生成的表中
     */
    @XxlJob("savePointsBoardToDb") // 任务名称
    public void savePointsBoardToDb() {
        // 1. 获取上个月的时间
        LocalDate lastMonth = LocalDate.now().minusMonths(1);
        // 2. 计算动态表名
        // 2.1 查询赛季 id
        Integer seasonId = seasonService.querySeasonIdByTime(lastMonth);
        if (seasonId == null) {
            return;// 赛季不存在
        }
        // 2.2 存入 ThreadLocal
        TableInfoContext.setInfo("points_board_" + seasonId);
        // 3. 查询 redis上月的榜单数据
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + lastMonth.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        int index = XxlJobHelper.getShardIndex();
        int total = XxlJobHelper.getShardTotal();
        int pageNo = index + 1, pageSize = 1000;
        // 由于数据量很大，所以分批查数据，如果单次查询的数据过多，可能造成网络堵塞，带宽占满，
        // 而且数据库单次写入的数据量也是有限的
        while (true) {
            List<PointsBoard> boardList = boardService.queryCurrentBoardList(key, pageNo, pageSize);
            if (CollUtils.isEmpty(boardList)) {
                break;
            }
            // 4. 持久化到数据库
            // 4.1 将排名信息写入 id
            boardList.forEach(e -> {
                e.setId(e.getRank().longValue());
                e.setRank(null);
            });
            // 4.2 持久化
            boardService.saveBatch(boardList);
            // 5. 翻页
            pageNo += total;
        }
        // 任务结束，移除动态表名
        TableInfoContext.remove();
    }

    /**
     * 定时清理 redis的数据
     */
    @XxlJob("clearPointsBoardFromRedis") // 任务名称
    public void clearPointsBoardFromRedis() {
        // 1. 获取上个月的时间
        LocalDate lastMonth = LocalDate.now().minusMonths(1);
        // 2. 计算 key
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + lastMonth.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        // 3. 异步删除 key
        redisTemplate.unlink(key);
    }
}

package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.pojo.PointsBoard;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardItemVO;
import com.tianji.learning.domain.vo.PointsBoardVO;
import com.tianji.learning.mapper.PointsBoardMapper;
import com.tianji.learning.service.IPointsBoardService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.utils.TableInfoContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.BoundZSetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 学霸天梯榜 服务实现类
 * </p>
 *
 * @author QianCCC
 * @since 2024-01-28
 */
@Service
@RequiredArgsConstructor
public class PointsBoardServiceImpl extends ServiceImpl<PointsBoardMapper, PointsBoard> implements IPointsBoardService {
    private final StringRedisTemplate redisTemplate;
    private final UserClient userClient;

    /**
     * 分页查询指定赛季的学霸积分排行榜
     */
    @Override
    public PointsBoardVO queryPointsBoardBySeason(PointsBoardQuery query) {
        // 1. 判断是否查询当前赛季
        Long season = query.getSeason();
        boolean isCurrentSeason = Objects.isNull(season) || season == 0;// 为空或者为0则查询当前赛季
        // 2. 查询我的积分和排名
        LocalDate now = LocalDate.now();
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + now.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        PointsBoard myBoard = isCurrentSeason ?
                queryMyCurrentBoard(key):  // 查询我在当前赛季榜单的排名和积分等(redis)
                queryMyHistoryBoard(season); // 查询我在历史赛季榜单的排名和积分等(数据库)
        // 3. 查询榜单列表
        List<PointsBoard> historyBoard = isCurrentSeason ?
                queryCurrentBoardList(key, query.getPageNo(), query.getPageSize()):  // 查询当前赛季榜单列表(redis)
                queryHistoryBoardList(query); // 查询历史赛季榜单列表(数据库)
        // 4. 封装 VO
        PointsBoardVO pointsBoardVO = new PointsBoardVO();
        // 4.1 处理我的信息
        pointsBoardVO.setPoints(myBoard.getPoints());
        pointsBoardVO.setRank(myBoard.getRank());
        if (CollUtils.isEmpty(historyBoard)) {
            return pointsBoardVO;
        }
        // 4.2 处理榜单信息
        // 4.2.1 封装用户信息
        Set<Long> userIds = historyBoard.stream().map(PointsBoard::getUserId).collect(Collectors.toSet());
        List<UserDTO> users = userClient.queryUserByIds(userIds);
        Map<Long, String> userMap = new HashMap<>(users.size());
        if (CollUtils.isNotEmpty(users)) {
            for (UserDTO user : users) {
                userMap.put(user.getId(), user.getName());
            }
        }
        List<PointsBoardItemVO> itemVOS = new ArrayList<>(historyBoard.size());
        // 4.2.2 填充用户名
        for (PointsBoard board : historyBoard) {// 遍历查询到的赛季榜单列表
            PointsBoardItemVO vo = new PointsBoardItemVO();
            vo.setPoints(board.getPoints());
            vo.setRank(board.getRank());
            vo.setName(userMap.get(board.getUserId()));
            itemVOS.add(vo);
        }
        pointsBoardVO.setBoardList(itemVOS);
        return pointsBoardVO;
    }

    /**
     * 查询当前登录用户在当前赛季榜单的排名和积分等(redis)
     */
    private PointsBoard queryMyCurrentBoard(String key) {
        // 提前绑定 key，后续进行操作时就不需要再指定 key了
        BoundZSetOperations<String, String> ops = redisTemplate.boundZSetOps(key);
        // 1. 查询积分
        Double score = ops.score(UserContext.getUser().toString());
        // 2. 查询排名
        Long rank = ops.reverseRank(UserContext.getUser().toString());// 注意使用倒序
        // 3. 封装返回
        PointsBoard pointsBoard = new PointsBoard();
        pointsBoard.setPoints(Objects.nonNull(score) ? score.intValue() : 0);
        pointsBoard.setRank(Objects.nonNull(rank) ? rank.intValue() + 1 : 0);
        return pointsBoard;
    }

    /**
     * 查询当前赛季榜单列表(redis)
     */
    @Override
    public List<PointsBoard> queryCurrentBoardList(String key, Integer pageNo, Integer pageSize) {
        // 1. 计算分页，即当前页第一个用户的排名
        int from = (pageNo - 1) * pageSize;
        // 2. 查询
        Set<ZSetOperations.TypedTuple<String>> rangeWithScores =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, from, from + pageSize - 1);
        if (CollUtils.isEmpty(rangeWithScores)) {
            return CollUtils.emptyList();
        }
        // 3. 封装
        int rank = from + 1;
        List<PointsBoard> list = new ArrayList<>(rangeWithScores.size());
        for (ZSetOperations.TypedTuple<String> r : rangeWithScores) {
            Double score = r.getScore();
            String userId = r.getValue();
            if (score == null || userId == null) continue;
            PointsBoard pointsBoard = new PointsBoard();
            pointsBoard.setPoints(score.intValue());
            pointsBoard.setUserId(Long.valueOf(userId));
            pointsBoard.setRank(rank++);// 使用一个计数器即可获取当前用户的排名
            list.add(pointsBoard);
        }
        return list;
    }

    /**
     * 查询我在历史赛季榜单的排名和积分等(数据库)
     */
    private PointsBoard queryMyHistoryBoard(Long seasonId) {
        // 1. 向 ThreadLocal传入新表名
        TableInfoContext.setInfo("points_board_" + seasonId);
        // 2. 获取当前登录用户
        Long userId = UserContext.getUser();
        // 3. 查询数据
        PointsBoard pointsBoard = lambdaQuery()
                .eq(PointsBoard::getUserId, userId)
                .one();
        // 4. 封装排名
        pointsBoard.setRank(pointsBoard.getId().intValue());

        return pointsBoard;
    }

    /**
     * 查询历史赛季榜单列表(数据库)
     */
    private List<PointsBoard> queryHistoryBoardList(PointsBoardQuery query) {
        // 1. 向 ThreadLocal传入新表名
        Long seasonId = query.getSeason();
        TableInfoContext.setInfo("points_board_" + seasonId);
        // 2. 分页查询
        Page<PointsBoard> page = lambdaQuery().page(query.toMpPage());
        List<PointsBoard> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return CollUtils.emptyList();
        }
        // 3. 封装排名
        for (PointsBoard record : records) {
            record.setRank(record.getId().intValue());
        }
        return records;
    }

    /**
     * 利用赛季 id生成数据表
     */
    @Override
    public void createTableBySeasonId(Integer seasonId) {
        getBaseMapper().createTableBySeasonId("points_board_" + seasonId);
    }
}

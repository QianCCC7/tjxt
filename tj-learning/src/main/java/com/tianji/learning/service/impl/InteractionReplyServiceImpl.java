package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.remark.RemarkClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.pojo.InteractionQuestion;
import com.tianji.learning.domain.pojo.InteractionReply;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.mq.message.SignInMessage;
import com.tianji.learning.service.IInteractionQuestionService;
import com.tianji.learning.service.IInteractionReplyService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.tianji.common.constants.Constant.DATA_FIELD_NAME_CREATE_TIME;
import static com.tianji.common.constants.Constant.DATA_FIELD_NAME_LIKED_TIME;

/**
 * <p>
 * 互动问题的回答或评论 服务实现类
 * </p>
 *
 * @author QianCCC
 * @since 2024-01-15
 */
@Service
@RequiredArgsConstructor
public class InteractionReplyServiceImpl extends ServiceImpl<InteractionReplyMapper, InteractionReply> implements IInteractionReplyService {

    private final IInteractionQuestionService questionService;
    private final UserClient userClient;
    private final RemarkClient remarkClient;
    private final RabbitMqHelper mqHelper;

    /**
     * 新增回答或评论
     */
    @Override
    @Transactional
    public void saveReply(ReplyDTO reply) {
        // 1. 获取当前登录用户
        Long userId = UserContext.getUser();
        // 2. 新增回答
        InteractionReply interactionReply = BeanUtils.copyBean(reply, InteractionReply.class);
        interactionReply.setUserId(userId);
        save(interactionReply);
        // 3. 累加评论数或者累加回答数
        // 3.1 该恢复是否为回答
        boolean isAnswer = reply.getAnswerId() == null;
        if (!isAnswer) {
            // 3.2 是评论，则更新该回答下的评论数量
            lambdaUpdate()
                    .setSql("reply_times = reply_times + 1")
                    .eq(InteractionReply::getId, interactionReply.getAnswerId()) // 上级 id
                    .update();

        } else {
            // 3.3 是回答，则更新回答表中最近一次回答以及回答数量
            questionService.lambdaUpdate()
                    .eq(InteractionQuestion::getId, reply.getQuestionId())
                    .set(InteractionQuestion::getLatestAnswerId, reply.getAnswerId()) // 最近一次回答的问题 id
                    .setSql("answer_times = answer_times + 1") // 更新回答数量
                    .set(reply.getIsStudent(), InteractionQuestion::getStatus, QuestionStatus.UN_CHECK.getValue()) // 更新查看状态
                    .update();
        }
        // 4. 尝试累加积分
        if (isAnswer && reply.getIsStudent()) {
            mqHelper.send(MqConstants.Exchange.LEARNING_EXCHANGE,
                    MqConstants.Key.WRITE_REPLY,
                    SignInMessage.of(userId, 5)
                    );
        }
    }

    /**
     * 分页查询回答或评论列表
     */
    @Override
    public PageDTO<ReplyVO> queryReplyPage(ReplyPageQuery query, boolean forAdmin) {
        // 1.问题id和回答id至少要有一个，先做参数判断
        Long questionId = query.getQuestionId();
        Long answerId = query.getAnswerId();
        if (Objects.isNull(questionId) && Objects.isNull(answerId)) {
            throw new BadRequestException("问题或回答id不能都为空");
        }
        // 标记是否为查询该问题下的所有回答,而不是评论
        boolean isQueryAnswer = Objects.nonNull(questionId);
        // 2. 分页查询所有 reply
        Page<InteractionReply> page = lambdaQuery()
                .eq(isQueryAnswer, InteractionReply::getQuestionId, questionId) // 该问题下的 reply
                .eq(InteractionReply::getAnswerId, isQueryAnswer ? 0L : answerId) // 没有上级
                .eq(!forAdmin, InteractionReply::getHidden, false)
                .page(query.toMpPage(// 先根据点赞数排序，点赞数相同，再按照创建时间排序
                        new OrderItem(DATA_FIELD_NAME_LIKED_TIME, false),
                        new OrderItem(DATA_FIELD_NAME_CREATE_TIME, true)
                ));
        List<InteractionReply> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        // 3. 数据查询：提问者信息、回复目标信息、当前用户是否点赞
        Set<Long> userIds = new HashSet<>(), answerIds = new HashSet<>(), targetReplyIds = new HashSet<>();
        // 3.1 获取提问者id 、回复的目标id、当前回答或评论id（统计点赞信息）
        for (InteractionReply record : records) {
            // 用户没有匿名或者是管理员查看，就添加用户 id
            if (!record.getAnonymity() || forAdmin) {
                userIds.add(record.getUserId());
            }
            answerIds.add(record.getId());
            targetReplyIds.add(record.getTargetReplyId());// 当查询的是回答下的评论才会有 targetReplyIds
        }
        targetReplyIds.remove(0L);
        targetReplyIds.remove(null);
        // 3.2 查询目标回复，如果目标回复不是匿名，则需要查询出目标回复的用户信息
        if (targetReplyIds.size() > 0) {
            List<InteractionReply> targetReplies = listByIds(targetReplyIds);
            Set<Long> targetUserIds = targetReplies.stream()
                    .filter(Predicate.not(InteractionReply::getAnonymity).or(f -> forAdmin))
                    .map(InteractionReply::getUserId)
                    .collect(Collectors.toSet());
            userIds.addAll(targetUserIds);
        }
        // 3.3 查询用户
        Map<Long, UserDTO> userMap = new HashMap<>(userIds.size());
        if (userIds.size() > 0) {
            List<UserDTO> userDTOS = userClient.queryUserByIds(userIds);
            userMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, u -> u));
        }
        // 3.4 查询用户点赞状态
        Set<Long> bizLiked = remarkClient.isBizLiked(answerIds);
        // 4. 封装 VO
        List<ReplyVO> list = new ArrayList<>(records.size());
        for (InteractionReply record : records) {
            ReplyVO vo = BeanUtils.copyBean(record, ReplyVO.class);
            // 4.1 回复人信息
            if (!record.getAnonymity() || forAdmin) {
                UserDTO user = userMap.get(record.getUserId());
                if (Objects.nonNull(user)) {
                    vo.setUserId(user.getId());
                    vo.setUserName(user.getName());
                    vo.setUserIcon(user.getIcon());
                }
            }
            // 4.2 如果存在评论的目标，即是评论，则需要设置目标用户信息
            if (Objects.nonNull(record.getTargetReplyId())) {
                UserDTO user = userMap.get(record.getTargetUserId());
                if (Objects.nonNull(user)) {
                    vo.setTargetUserName(user.getName());
                }
            }
            // 4.3 点赞状态
            vo.setLiked(CollUtils.isNotEmpty(bizLiked) && bizLiked.contains(record.getId()));
            list.add(vo);
        }
        return PageDTO.of(page, list);
    }

    /**
     * 管理端隐藏或显示回答或评论
     */
    @Override
    public void hiddenReply(Long id, Boolean hidden) {
        // 1. 获取回答或评论
        InteractionReply reply = getById(id);
        if (Objects.isNull(reply)) {
            throw new BadRequestException("回答或评论不存在");
        }
        // 2. 隐藏某个回答或者评论
        InteractionReply interactionReply = new InteractionReply();
        interactionReply.setId(id);
        interactionReply.setHidden(hidden);
        updateById(interactionReply);
        // 3. 如果隐藏的是回答，那么该回答下的所有评论也要隐藏
        if (reply.getAnswerId() != null && reply.getAnswerId() != 0) {
            // 3.1 有上级，说明自己就是评论，无需处理
            return;
        }
        // 3.2 隐藏回答下的评论
        lambdaUpdate()
                .eq(InteractionReply::getAnswerId, id)
                .set(InteractionReply::getHidden, hidden)
                .update();
    }
}

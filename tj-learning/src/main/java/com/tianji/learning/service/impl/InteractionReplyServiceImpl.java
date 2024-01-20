package com.tianji.learning.service.impl;

import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.pojo.InteractionQuestion;
import com.tianji.learning.domain.pojo.InteractionReply;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.tianji.learning.service.IInteractionReplyService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        // TODO 4. 尝试累加积分
    }
}

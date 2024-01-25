package com.tianji.learning.mq;

import com.tianji.api.remark.LikeTimesDTO;
import com.tianji.common.constants.MqConstants;
import com.tianji.learning.domain.pojo.InteractionReply;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikeTimesChangeListener {

    private final IInteractionReplyService replyService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "qa.liked.times.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.LIKE_RECORD_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.QA_LIKED_TIMES_KEY
    ))
    public void listenReplyLikedTimesChange(List<LikeTimesDTO> likeTimesDTO) {
        log.debug("监听到回答或评论的点赞数变更消息");
        List<InteractionReply> replies = new ArrayList<>(likeTimesDTO.size());
        for (LikeTimesDTO timesDTO : likeTimesDTO) {
            InteractionReply reply = new InteractionReply();
            reply.setId(timesDTO.getBizId());
            reply.setLikedTimes(timesDTO.getLikeTimes());
            replies.add(reply);
        }
        replyService.updateBatchById(replies);
    }
}

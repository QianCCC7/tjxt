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
    public void listenReplyLikedTimesChange(LikeTimesDTO likeTimesDTO) {
        log.debug("监听到回答或评论的点赞数变更消息：{}, 点赞数{}",
                likeTimesDTO.getBizId(),
                likeTimesDTO.getLikeTimes());
        InteractionReply reply = new InteractionReply();
        reply.setId(likeTimesDTO.getBizId());
        reply.setLikedTimes(likeTimesDTO.getLikeTimes());
        replyService.updateById(reply);
    }
}

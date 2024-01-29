package com.tianji.learning.mq;

import com.tianji.common.constants.MqConstants;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mq.message.SignInMessage;
import com.tianji.learning.service.IPointsRecordService;
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
public class LearningPointsListener {
    private final IPointsRecordService pointsRecordService;

    /**
     * 监听签到的消息，用于新增积分
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "sign.points.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.LEARNING_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.SIGN_IN
    ))
    // 由于该方法获取到的积分是动态的，所以通过实体类封装积分和 userId
    public void listenSignMessage(SignInMessage message) {
        log.debug("监听到签到消息...");
        pointsRecordService.addPointsRecord(message.getUserId(), message.getPoints(), PointsRecordType.SIGN);
    }

    /**
     * 监听写回答的消息，用于新增积分
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "qa.points.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.LEARNING_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.WRITE_REPLY
    ))
    public void listenWriteReplyMessage(Long userId) {
        pointsRecordService.addPointsRecord(userId, 5, PointsRecordType.QA);
    }

    /**
     * 监听学习的消息，用于新增积分
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "learning.points.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.LEARNING_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.LEARN_SECTION
    ))
    public void listenLearningMessage(Long userId) {
        pointsRecordService.addPointsRecord(userId, 10, PointsRecordType.LEARNING);
    }

    /**
     * 监听记录笔记的消息，用于新增积分
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "note.points.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.LEARNING_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.WRITE_NOTE
    ))
    public void listenNoteMessage(Long userId) {
        pointsRecordService.addPointsRecord(userId, 3, PointsRecordType.NOTE);
    }

    /**
     * 监听用户评价课程的消息，用于新增积分
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "comment.points.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.LEARNING_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.WRITE_COMMENT
    ))
    public void listenCommentMessage(Long userId) {
        pointsRecordService.addPointsRecord(userId, 10, PointsRecordType.COMMENT);
    }
}

package com.tianji.learning.mq;

import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.constants.MqConstants;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Objects;

/**
 * 接口功能：当用户购买完课程后，异步监听消息将课程添加至用户课程表
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LessonsChangeListener {

    private final ILearningLessonService learningLessonService;

    /**
     * 监听支付成功，添加课程
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "learning.lesson.pay.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.ORDER_PAY_KEY
    ))
    public void listenLessonPay(OrderBasicDTO orderBasicDTO) {
        // 1. 健壮性处理
        if (Objects.isNull(orderBasicDTO)
                || Objects.isNull(orderBasicDTO.getUserId())
                || CollectionUtils.isEmpty(orderBasicDTO.getCourseIds())) {
            log.error("接收到的MQ消息有误，订单数据为空");
            return;
        }
        // 2. 添加课程
        log.debug("监听到用户{}的订单{},需要将课程{}添加到课程表中",
                orderBasicDTO.getUserId(), orderBasicDTO.getOrderId(), orderBasicDTO.getCourseIds());
        learningLessonService.addUserLessons(orderBasicDTO.getUserId(), orderBasicDTO.getCourseIds());
    }

    /**
     * 监听到退款消息，删除课程
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "learning.lesson.refund.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.ORDER_REFUND_KEY
    ))
    public void listenLessonRefund(OrderBasicDTO orderBasicDTO) {
        // 1. 健壮性处理
        if (Objects.isNull(orderBasicDTO)
                || Objects.isNull(orderBasicDTO.getOrderId())
                || Objects.isNull(orderBasicDTO.getUserId())
                || CollectionUtils.isEmpty(orderBasicDTO.getCourseIds())) {
            log.error("接收到的MQ消息有误，订单数据为空");
            return;
        }
        // 2. 删除课程
        log.debug("监听到用户要退款{}的订单{},需要将课程{}从课表中删除",
                orderBasicDTO.getUserId(), orderBasicDTO.getOrderId(), orderBasicDTO.getCourseIds());
        learningLessonService.removeUserLessons(orderBasicDTO.getUserId(), orderBasicDTO.getCourseIds().get(0));
    }
}

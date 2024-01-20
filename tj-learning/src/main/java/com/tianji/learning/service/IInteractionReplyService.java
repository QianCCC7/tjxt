package com.tianji.learning.service;

import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.pojo.InteractionReply;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 互动问题的回答或评论 服务类
 * </p>
 *
 * @author QianCCC
 * @since 2024-01-15
 */
public interface IInteractionReplyService extends IService<InteractionReply> {

    void saveReply(ReplyDTO reply);
}

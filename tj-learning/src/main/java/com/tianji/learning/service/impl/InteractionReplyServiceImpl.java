package com.tianji.learning.service.impl;

import com.tianji.learning.domain.pojo.InteractionReply;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionReplyService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 互动问题的回答或评论 服务实现类
 * </p>
 *
 * @author QianCCC
 * @since 2024-01-15
 */
@Service
public class InteractionReplyServiceImpl extends ServiceImpl<InteractionReplyMapper, InteractionReply> implements IInteractionReplyService {

}

package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.pojo.InteractionReply;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;

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

    PageDTO<ReplyVO> queryReplyPage(ReplyPageQuery query, boolean forAdmin);

    void hiddenReply(Long id, Boolean hidden);
}

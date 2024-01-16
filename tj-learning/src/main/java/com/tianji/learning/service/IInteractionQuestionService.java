package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.pojo.InteractionQuestion;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionVO;

/**
 * <p>
 * 互动提问的问题表 服务类
 * </p>
 *
 * @author QianCCC
 * @since 2024-01-15
 */
public interface IInteractionQuestionService extends IService<InteractionQuestion> {

    void saveQuestion(QuestionFormDTO questionFormDTO);

    PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery pageQuery);
}

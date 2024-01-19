package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.pojo.InteractionQuestion;
import com.tianji.learning.domain.pojo.InteractionReply;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动提问的问题表 服务实现类
 * </p>
 *
 * @author QianCCC
 * @since 2024-01-15
 */
@Service
@RequiredArgsConstructor
public class InteractionQuestionServiceImpl extends ServiceImpl<InteractionQuestionMapper, InteractionQuestion> implements IInteractionQuestionService {

    private final UserClient userClient;
    private final IInteractionReplyService replyService;

    /**
     * 提出问题接口
     * @param questionFormDTO
     */
    @Override
    public void saveQuestion(QuestionFormDTO questionFormDTO) {
        // 1. 获取当前登录用户
        Long userId = UserContext.getUser();
        // 2. 数据封装
        InteractionQuestion interactionQuestion = BeanUtils.copyBean(questionFormDTO, InteractionQuestion.class);
        interactionQuestion.setUserId(userId);
        // 3. 写入数据库
        save(interactionQuestion);
    }

    /**
     * 分页查询互动问题
     * @param pageQuery
     */
    @Override
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery pageQuery) {
        // 1. 参数校验，课程 id和小结 id不能都为空
        Long courseId = pageQuery.getCourseId(), sectionId = pageQuery.getSectionId();
        if (Objects.isNull(courseId) && Objects.isNull(sectionId)) {
            throw new BadRequestException("课程 id和小结 id不能都为空");
        }
        // 2. 分页查询
        Page<InteractionQuestion> page = lambdaQuery()
                .select(InteractionQuestion.class, po -> !po.getProperty().equals("description")) // 不查询 description字段
                .eq(pageQuery.getOnlyMine(), InteractionQuestion::getUserId, UserContext.getUser()) // 查询自己的问题
                .eq(Objects.nonNull(courseId), InteractionQuestion::getCourseId, courseId)
                .eq(Objects.nonNull(sectionId), InteractionQuestion::getSectionId, sectionId)
                .eq(InteractionQuestion::getHidden, false) // 不隐藏的问题
                .page(pageQuery.toMpPageDefaultSortByCreateTimeDesc());// 根据提问时间降序排序
        List<InteractionQuestion> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        // 3. 封装每个问题的提问者信息及其最新一次回答的信息
        // 3.1 统计所有提问者的 id以及每个问题新一次回答的回答者的 id
        Set<Long> userIds = new HashSet<>(), answerIds = new HashSet<>();
        for (InteractionQuestion question : records) {
            if (!question.getAnonymity()) {// 统计所有提问者的 id且非匿名的用户 id
                userIds.add(question.getUserId());//
            }
            answerIds.add(question.getLatestAnswerId());
        }
        userIds.remove(null);
        answerIds.remove(null);
        // 3.1 根据问题 id查询其最新一次回答的信息
        Map<Long, InteractionReply> replyMap = new HashMap<>(answerIds.size());
        if (CollUtils.isNotEmpty(answerIds)) {
            List<InteractionReply> replies = replyService.listByIds(answerIds);
            for (InteractionReply reply : replies) {
                replyMap.put(reply.getId(), reply);
                if (!reply.getAnonymity()) {
                    userIds.add(reply.getUserId());// 统计每个问题新一次回答的回答者的 id
                }
            }
        }
        // 3.2 根据问题 id查询其提问者信息
        Map<Long, UserDTO> userMap = new HashMap<>(userIds.size());
        if (CollUtils.isNotEmpty(userIds)) {
            List<UserDTO> userDTOS = userClient.queryUserByIds(userIds);
            userMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, u -> u));
        }
        // 4. 封装 vo
        List<QuestionVO> voList = new ArrayList<>(records.size());
        for (InteractionQuestion record : records) {
            // 4.1 po转 vo
            QuestionVO questionVO = BeanUtils.copyBean(record, QuestionVO.class);
            // 4.2 封装问题提问者信息
            if (!record.getAnonymity()) {
                UserDTO userDTO = userMap.get(record.getUserId());
                if (Objects.nonNull(userDTO)) {
                    questionVO.setUserName(userDTO.getName());
                    questionVO.setUserIcon(userDTO.getIcon());
                }
            }
            // 4.3 封装问题最新一次回答信息
            InteractionReply reply = replyMap.get(record.getLatestAnswerId());
            if (Objects.nonNull(reply)) {
                questionVO.setLatestReplyContent(reply.getContent());
                if (!reply.getAnonymity()) {
                    UserDTO user = userMap.get(reply.getUserId());
                    questionVO.setLatestReplyUser(user.getName());
                }
            }
            voList.add(questionVO);
        }

        return PageDTO.of(page, voList);
    }

    /**
     * 根据问题 id查询指定问题详情
     */
    @Override
    public QuestionVO queryQuestionById(Long id) {
        // 1. 根据 id查询数据
        InteractionQuestion question = getById(id);
        // 2. 数据校验
        if (Objects.isNull(question) || question.getHidden()) {
            return null;// 没有数据或者被隐藏
        }
        // 3. 查询提问者信息
        UserDTO user = null;
        if (!question.getAnonymity()) {
            user = userClient.queryUserById(question.getUserId());
        }
        // 4. 封装 vo
        QuestionVO questionVO = BeanUtils.copyBean(question, QuestionVO.class);
        if (Objects.nonNull(user)) {
            questionVO.setUserName(user.getName());
            questionVO.setUserIcon(user.getIcon());
        }
        return questionVO;
    }
}

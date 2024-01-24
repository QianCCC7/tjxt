package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.pojo.InteractionQuestion;
import com.tianji.learning.domain.pojo.InteractionReply;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
    private final InteractionReplyMapper replyMapper;
    private final SearchClient searchClient;
    private final CourseClient courseClient;
    private final CatalogueClient catalogueClient;
    private final CategoryCache categoryCache;

    /**
     * 提出问题接口
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
     * 修改互动问题
     */
    @Override
    public void updateQuestion(Long id, QuestionFormDTO questionFormDTO) {
        // 1. 获取当前登录用户
        Long userId = UserContext.getUser();
        // 2. 获取要修改的问题
        InteractionQuestion question = getById(id);
        if (Objects.isNull(question)) {
            throw new BadRequestException("问题不存在");
        }
        // 3. 判断是否为该用户的问题
        if (!question.getUserId().equals(userId)) {
            throw new BadRequestException("无法修改他人问题");
        }
        // 4. 修改问题
        InteractionQuestion res = BeanUtils.copyBean(questionFormDTO, InteractionQuestion.class);
        res.setId(id);// 注意 id不能变
        updateById(res);
    }

    /**
     * 删除互动问题
     */
    @Override
    public void deleteQuestion(Long id) {
        // 1. 判断问题是否存在
        InteractionQuestion question = getById(id);
        if (Objects.isNull(question)) {
            throw new BadRequestException("问题不存在");
        }
        // 2. 判断该问题是否属于当前登录用户
        Long userId = UserContext.getUser();
        if (!question.getUserId().equals(userId)) {
            throw new BadRequestException("无权删除他人的问题");
        }
        // 3. 删除问题
        removeById(id);
        // 4. 删除该问题下的所有回答以及评论
        LambdaQueryWrapper<InteractionReply> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(InteractionReply::getQuestionId, id);
        replyMapper.delete(queryWrapper);
    }

    /**
     * 分页查询互动问题
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
            List<InteractionReply> replies = replyMapper.selectBatchIds(answerIds);
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

    /**
     * 管理端查询分页查询互动问题
     */
    @Override
    public PageDTO<QuestionAdminVO> queryQuestionPageAdmin(QuestionAdminPageQuery pageQuery) {
        // 1. 根据课程名称，获取所有满足条件的课程 id
        List<Long> coursesIds = null;
        String courseName = pageQuery.getCourseName();
        if (StringUtils.isNotBlank(courseName)) {// 要根据课程名称查询问题
            coursesIds = searchClient.queryCoursesIdByName(courseName);
            if (CollUtils.isEmpty(coursesIds)) {// 为空，没有数据
                return PageDTO.empty(0L, 0L);
            }
        }
        // 2. 分页查询
        Integer status = pageQuery.getStatus();
        LocalDateTime beginTime = pageQuery.getBeginTime();
        LocalDateTime endTime = pageQuery.getEndTime();
        Page<InteractionQuestion> page = lambdaQuery()
                .in(coursesIds != null, InteractionQuestion::getCourseId, coursesIds)
                .eq(status != null, InteractionQuestion::getStatus, status)
                .gt(beginTime != null, InteractionQuestion::getCreateTime, beginTime)
                .lt(endTime != null, InteractionQuestion::getCreateTime, endTime)
                .page(pageQuery.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }

        // 3. 准备 vo其他数据：用户数据、课程数据、章节数据、分类数据
        Set<Long> userIds = new HashSet<>(), courseIds = new HashSet<>(), cataIds = new HashSet<>();
        // 3.1 获取问题数据的 id集合
        for (InteractionQuestion record : records) {
            userIds.add(record.getUserId());
            courseIds.add(record.getCourseId());
            cataIds.add(record.getChapterId());
            cataIds.add(record.getSectionId());
        }
        // 3.2 根据 id查询用户
        List<UserDTO> users = userClient.queryUserByIds(userIds);
        Map<Long, UserDTO> userMap = new HashMap<>(users.size());
        if (CollUtils.isNotEmpty(users)) {
            userMap = users
                    .stream()
                    .collect(Collectors.toMap(UserDTO::getId, u -> u));
        }
        // 3.3 根据 id查询课程
        List<CourseSimpleInfoDTO> courseSimpleInfoDTOS = courseClient.getSimpleInfoList(courseIds);
        Map<Long, CourseSimpleInfoDTO> courseMap = new HashMap<>(courseSimpleInfoDTOS.size());
        if (CollUtils.isNotEmpty(courseSimpleInfoDTOS)) {
            courseMap = courseSimpleInfoDTOS
                    .stream()
                    .collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        }
        // 3.4 根据 id查询章节
        List<CataSimpleInfoDTO> cataSimpleInfoDTOS = catalogueClient.batchQueryCatalogue(cataIds);
        Map<Long, String> cataMap = new HashMap<>(cataSimpleInfoDTOS.size());
        if (CollUtils.isNotEmpty(cataSimpleInfoDTOS)) {
            cataMap = cataSimpleInfoDTOS
                    .stream()
                    .collect(Collectors.toMap(CataSimpleInfoDTO::getId, CataSimpleInfoDTO::getName));
        }
        // 3.5 根据 id查询分类
        // 4. 封装 vo
        List<QuestionAdminVO> questionAdminVOS = new ArrayList<>(records.size());
        for (InteractionQuestion record : records) {
            // 4.1 po转 vo
            QuestionAdminVO questionAdminVO = BeanUtils.copyBean(record, QuestionAdminVO.class);
            // 4.2 用户信息
            UserDTO userDTO = userMap.get(record.getUserId());
            if (Objects.nonNull(userDTO)) {
                questionAdminVO.setUserName(userDTO.getName());
            }
            // 4.3 课程以及分类信息
            CourseSimpleInfoDTO cInfo = courseMap.get(record.getCourseId());
            if (Objects.nonNull(cInfo)) {
                questionAdminVO.setCourseName(cInfo.getName());
                String categoryNames = categoryCache.getCategoryNames(cInfo.getCategoryIds());
                questionAdminVO.setCategoryName(categoryNames);
            }
            // 4.4 章节信息
            questionAdminVO.setChapterName(cataMap.getOrDefault(record.getChapterId(), ""));
            questionAdminVO.setSectionName(cataMap.getOrDefault(record.getSectionId(), ""));

            questionAdminVOS.add(questionAdminVO);
        }
        return PageDTO.of(page, questionAdminVOS);
    }

    /**
     * 管理端根据问题 id查询指定问题详情
     */
    @Override
    public QuestionAdminVO queryQuestionByIdAdmin(Long id) {
        // 1. 根据id查询问题
        InteractionQuestion question = getById(id);
        if (Objects.isNull(question)) {
            return null;
        }
        // 2. 转PO为VO
        QuestionAdminVO vo = BeanUtils.copyBean(question, QuestionAdminVO.class);
        // 3. 提问者信息
        UserDTO user = userClient.queryUserById(question.getUserId());
        if (Objects.nonNull(user)) {
            vo.setUserName(user.getName());
            vo.setUserIcon(user.getIcon());
        }
        // 4. 课程信息
        CourseFullInfoDTO course = courseClient.getCourseInfoById(question.getCourseId(), false, true);
        if (Objects.nonNull(course)) {
            vo.setCourseName(course.getName());// 课程名称
            List<Long> teacherIds = course.getTeacherIds();
            List<UserDTO> teachers = userClient.queryUserByIds(teacherIds);
            if (CollUtils.isNotEmpty(teachers)) {
                vo.setTeacherName(teachers.stream().map(UserDTO::getName).collect(Collectors.joining("/")));
            }
            String categoryNames = categoryCache.getCategoryNames(course.getCategoryIds());
            if (Objects.nonNull(categoryNames)) {
                vo.setCategoryName(categoryNames);// 三级分类名称，中间使用/隔开
            }
        }
        // 5. 章节信息
        List<Long> cataIds = List.of(question.getSectionId(), question.getChapterId());
        List<CataSimpleInfoDTO> cataInfos = catalogueClient.batchQueryCatalogue(cataIds);
        Map<Long, String> cataMap = new HashMap<>(cataInfos.size());
        if (CollUtils.isNotEmpty(cataInfos)) {
            cataMap = cataInfos
                    .stream()
                    .collect(Collectors.toMap(CataSimpleInfoDTO::getId, CataSimpleInfoDTO::getName));
        }
        vo.setSectionName(cataMap.getOrDefault(question.getSectionId(), ""));
        vo.setChapterName(cataMap.getOrDefault(question.getChapterId(), ""));
        question.setStatus(QuestionStatus.CHECKED);
        updateById(question);// 更新该问题的状态为已查看
        return vo;
    }

    /**
     * 管理端显示或隐藏问题
     */
    @Override
    public void hiddenQuestion(Long id, Boolean hidden) {
        InteractionQuestion question = getById(id);
        if (Objects.isNull(question)) {
            throw new BadRequestException("问题不存在");
        }
        if (!question.getHidden().equals(hidden)) {
            question.setHidden(hidden);
            question.setId(id);
            updateById(question);
        }
    }
}

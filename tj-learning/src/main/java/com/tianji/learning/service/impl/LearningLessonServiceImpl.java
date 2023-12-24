package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.IdAndNumDTO;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.pojo.LearningRecord;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LearningPlanVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.domain.pojo.LearningLesson;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 学生课程表 服务实现类
 *
 * @author QianCCC
 * @since 2023-12-06 23:59
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {

    private final CourseClient courseClient;
    private final CatalogueClient catalogueClient;
    private final LearningRecordMapper recordMapper;

    /**
     * 批量添加课程至课程表
     *
     * @param userId    为购买课程的用户 id
     * @param courseIds 为用户所有将要添加至课程表的课程 id集合
     */
    @Override
    @Transactional
    public void addUserLessons(Long userId, List<Long> courseIds) {
        // 1. 查询课程简单信息，包含课程有效期等
        List<CourseSimpleInfoDTO> coursesInfoList = courseClient.getSimpleInfoList(courseIds);
        if (CollectionUtils.isEmpty(coursesInfoList)) {
            log.error("课程信息不存在,无法添加到课表");
            return;
        }
        // 2. 循环遍历，封装数据
        List<LearningLesson> list = new ArrayList<>(coursesInfoList.size());
        for (CourseSimpleInfoDTO course : coursesInfoList) {
            LearningLesson learningLesson = new LearningLesson();
            // 2.1 获取课程有效期，计算过期时间
            Integer validDuration = course.getValidDuration();// 单位为月
            if (validDuration != null && validDuration > 0) {
                LocalDateTime now = LocalDateTime.now();
                learningLesson.setCreateTime(now);
                learningLesson.setExpireTime(now.plusMonths(validDuration));
            }
            // 2.2 填充用户其他信息
            learningLesson.setUserId(userId);
            learningLesson.setCourseId(course.getId());
            list.add(learningLesson);
        }
        // 3. 批量新增数据
        log.debug("新增用户课表开始");
        saveBatch(list);
    }

    /**
     * 分页查询我的课表
     *
     * @param pageQuery
     * @return
     */
    @Override
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery pageQuery) {
        // 1. 获取当前用户
        Long userId = UserContext.getUser();
        // select * from learning_lesson where user_id = #{userId} order by latest_learn_time limit 0,5
        // 2.分页查询我的课表
        Page<LearningLesson> page = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .page(pageQuery.toMpPage("latest_learn_time", false));// 默认按照学习时间降序排序
        List<LearningLesson> records = page.getRecords();// 课表所有的课程信息
        if (CollectionUtils.isEmpty(records)) {
            return PageDTO.empty(page);// 返回一个空的课程信息
        }
        // 3.查询课程信息
        Map<Long, CourseSimpleInfoDTO> cMap = queryCourseSimpleInfoList(records);
        // 4.封装查询到的课表数据为 LearningLessonVO
        List<LearningLessonVO> list = new ArrayList<>(records.size());
        for (LearningLesson record : records) {
            // 4.1 拷贝 LearningLesson属性名与LearningLessonVO相同的属性到vo中
            LearningLessonVO lessonVO = new LearningLessonVO();
            BeanUtils.copyProperties(record, lessonVO);
            // 4.2 获取课程的其他信息到vo
            CourseSimpleInfoDTO cInfo = cMap.get(record.getCourseId());
            lessonVO.setCourseName(cInfo.getName());
            lessonVO.setCourseCoverUrl(cInfo.getCoverUrl());
            lessonVO.setSections(cInfo.getSectionNum());
            list.add(lessonVO);
        }
        return PageDTO.of(page, list);// 封装好了分页参数
    }
    // 通过课程 ID集合批量查询课程信息并封装为 Map
    private Map<Long, CourseSimpleInfoDTO> queryCourseSimpleInfoList(List<LearningLesson> records) {
        // 获取课表中所有课程的 ID
        Set<Long> courseIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        // 根据所有课程 ID查询所有课程信息，用于封装 LearningLessonVO
        List<CourseSimpleInfoDTO> coursesInfoList = courseClient.getSimpleInfoList(courseIds);
        if (CollectionUtils.isEmpty(coursesInfoList)) {
            throw new BadRequestException("课程信息不存在！");
        }
        // 将课程集合处理为Map，方便下面封装数据时，通过课程ID获取到课程信息
        return coursesInfoList.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
    }

    /**
     * 查询正在学习的课程
     *
     * @return
     */
    @Override
    public LearningLessonVO queryMyCurrentLesson() {
        log.debug("正在查询");
        // 1. 获取当前登录用户
        Long userId = UserContext.getUser();
        // 2. 查询最后一条最近学习的课程
        // select * from learning_lesson where user_id = #{userId} and status = 1
        // order by latest_learn_time desc limit 1
        LearningLesson lastLearningLesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getStatus, LessonStatus.LEARNING.getValue()) // 为1代表学习中
                .orderByDesc(LearningLesson::getLatestLearnTime) // 最后学习的一门课
                .last("limit 1")
                .one();
        if (Objects.isNull(lastLearningLesson)) {
            return null;
        }
        // 3. 复制相同属性到 LearningLessonVO
        LearningLessonVO lessonVO = new LearningLessonVO();
        BeanUtils.copyProperties(lastLearningLesson, lessonVO);
        // 4. 查询课程其他信息(课程名称，课程封面以及总课时数)
        CourseFullInfoDTO courseInfo = courseClient.getCourseInfoById(lastLearningLesson.getCourseId(), false, false);
        lessonVO.setCourseName(courseInfo.getName());
        lessonVO.setCourseCoverUrl(courseInfo.getCoverUrl());
        lessonVO.setSections(courseInfo.getSectionNum());
        // 5. 统计课表中课程总数量
        Integer courseAmount = lambdaQuery().eq(LearningLesson::getUserId, userId).count();
        lessonVO.setCourseAmount(courseAmount);
        // 6. 查询最近一次学习的小结名称和小结序号，通过 catalogueClient查询目录的简单信息，包含小结名称以及小结序号
        List<CataSimpleInfoDTO> catalogueInfos =
                catalogueClient.batchQueryCatalogue(Collections.singletonList(lastLearningLesson.getLatestSectionId()));
        if (!CollUtils.isEmpty(catalogueInfos)) {
            CataSimpleInfoDTO catalogueInfo = catalogueInfos.get(0);
            lessonVO.setLatestSectionName(catalogueInfo.getName());
            lessonVO.setLatestSectionIndex(catalogueInfo.getCIndex());
        }
        return lessonVO;
    }

    /**
     * 用户退款时，删除课程
     *
     * @param userId
     * @param courseId
     */
    @Override
    @Transactional
    public void removeUserLessons(Long userId, Long courseId) {
        LambdaQueryWrapper<LearningLesson> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId);
        log.debug("删除指定课表开始");
        remove(queryWrapper);
    }

    /**
     * 用户删除已失效的课程
     *
     * @param courseId
     */
    @Override
    @Transactional
    public void deleteInvalidByCourseId(Long courseId) {
        if (Objects.isNull(courseId)) {
            throw new BadRequestException("课程不存在");
        }
        // 1. 获取当前登录用户
        Long userId = UserContext.getUser();
        // 2. 查询到要删的课程
        LearningLesson lesson = lambdaQuery().eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .eq(LearningLesson::getStatus, LessonStatus.EXPIRED)
                .one();
        if (Objects.isNull(lesson)) {
            throw new BadRequestException("该课程不是已失效的课程");
        }
        log.info("用户{}要删除的课程ID为{}的课程", userId, lesson.getCourseId());
        removeById(lesson.getId());
    }

    /**
     * 校验当前用户是否可以学习当前课程，即当前课程是否有效
     *
     * @param courseId
     * @return lessonId，如果是报名了则返回lessonId，否则返回空
     */
    @Override
    public Long isLessonValid(Long courseId) {
        if (Objects.isNull(courseId)) return null;
        // 1. 获取当前登录用户的 ID
        Long userId = UserContext.getUser();
        // 2. 查询用户课表中是否有该课程
        LearningLesson validLesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .ne(LearningLesson::getStatus, LessonStatus.EXPIRED.getValue()).one();

        if (Objects.isNull(validLesson)) {
            log.debug("用户{}没有该课程{}，或者课程已失效", userId, courseId);
            return null;
        }
        log.debug("用户{}的课程{}有效", userId, courseId);
        return validLesson.getId();
    }

    /**
     * 课程详情页用户课表中指定课程的状态动态展示，即查询用户课程的学习状态
     *
     * @param courseId
     * @return
     */
    @Override
    public LearningLessonVO queryLessonStatus(Long courseId) {
        // 1. 查询当前登录用户
        Long userId = UserContext.getUser();
        // 2. 查询当前用户是否有指定课程
        LearningLesson lesson = queryLessonByUserIdAndCourseId(userId, courseId);
        // 2.1 用户没有指定课程，那么返回 null，前端显示立即购买或者加入购物车等
        if (Objects.isNull(lesson)) {
            log.debug("用户{}没有指定课程{}", userId, courseId);
            return null;
        }
        // 2.2 用户拥有课程，封装课程信息并返回
        LearningLessonVO lessonVO = new LearningLessonVO();
        BeanUtils.copyProperties(lesson, lessonVO);
        log.debug("用户{}的课程{}的状态为{}", userId, courseId, lessonVO.getStatus().getDesc());
        return lessonVO;
    }

    /**
     * 根据用户 Id和课程 Id查询课表信息
     * @param userId
     * @param courseId
     * @return
     */
    @Override
    public LearningLesson queryLessonByUserIdAndCourseId(Long userId, Long courseId) {
        return lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId).one();
    }

    /**
     * 统计课程的学习人数
     *
     * @param courseId
     * @return
     */
    @Override
    public Integer countLearningLessonByCourse(Long courseId) {
        if (Objects.isNull(courseId)) {
            log.debug("课程{}不存在", courseId);
            return 0;
        }
        return lambdaQuery()
                .eq(LearningLesson::getCourseId, courseId)
                .in(LearningLesson::getStatus,
                        LessonStatus.NOT_BEGIN.getValue(),
                        LessonStatus.LEARNING.getValue(),
                        LessonStatus.FINISHED.getValue())
                .count();
    }

    /**
     * 创建及修改学习计划
     * @param planDTO
     */
    @Override
    public void createLearningPlan(LearningPlanDTO planDTO) {
        // 1. 查询登录用户
        Long userId = UserContext.getUser();
        // 2. 查询课表有关数据
        LearningLesson lesson = queryLessonByUserIdAndCourseId(userId, planDTO.getCourseId());
        if (Objects.isNull(lesson)) {
            throw new BadRequestException("课程信息不存在");// 课程不存在，无法创建学习计划
        }
        LearningLesson newLesson = new LearningLesson();
        newLesson.setId(lesson.getId());
        newLesson.setWeekFreq(planDTO.getFreq());
        if (lesson.getPlanStatus() == PlanStatus.NO_PLAN) {
            newLesson.setPlanStatus(PlanStatus.PLAN_RUNNING);
        }
        updateById(newLesson);
    }

    /**
     * 查询我的学习计划
     * @param pageQuery
     * @return
     */
    @Override
    public LearningPlanPageVO queryMyPlans(PageQuery pageQuery) {
        LearningPlanPageVO planPageVO = new LearningPlanPageVO();
        // 1. 获取当前用户
        Long userId = UserContext.getUser();
        // 2. 获取本周起始时间
        LocalDate now = LocalDate.now();
        LocalDateTime beginTime = DateUtils.getWeekBeginTime(now);
        LocalDateTime endTime = DateUtils.getWeekEndTime(now);
        // 3. 查询总的统计数据
        // 3.1 本周总的已学习小结数量
        Integer weekFinishedCount = recordMapper.selectCount(new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId)
                .eq(LearningRecord::getFinished, true)
                .gt(LearningRecord::getFinishTime, beginTime)
                .gt(LearningRecord::getFinishTime, endTime)
        );
        planPageVO.setWeekFinished(weekFinishedCount);
        // 3.2 本周总的计划学习小结数量
        Integer weekTotalPlansCount = getBaseMapper().queryTotalPlans(userId);
        planPageVO.setWeekTotalPlan(weekTotalPlansCount);
        // TODO 3.3 本周学习积分

        // 4. 查询分页数据
        // 4.1 分页查询计划中的课表信息以及学习计划信息
        Page<LearningLesson> page = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .in(LearningLesson::getStatus, LessonStatus.NOT_BEGIN, LessonStatus.LEARNING)
                .page(pageQuery.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = page.getRecords();
        if (CollectionUtils.isEmpty(records)) {
            return planPageVO.emptyPage(page);
        }
        // 4.2 查询课表对应的课程信息
        Map<Long, CourseSimpleInfoDTO> cMap = queryCourseSimpleInfoList(records);
        // 4.3 统计每个课程本周已学习小结数量
        List<IdAndNumDTO> list = recordMapper.learnedSectionsCount(userId, beginTime, endTime);
        Map<Long, Integer> map = IdAndNumDTO.toMap(list);// 封装为 map，可以快速找出某个课程本周已学习的小结数量
        // 4.4 封装VO
        List<LearningPlanVO> result = new ArrayList<>(records.size());
        for (LearningLesson record : records) {
            // 4.4.1 拷贝属性
            LearningPlanVO vo = com.tianji.common.utils.BeanUtils.copyBean(record, LearningPlanVO.class);
            // 4.4.2 填充课程详细信息
            CourseSimpleInfoDTO courseSimpleInfoDTO = cMap.get(record.getCourseId());
            if (!Objects.isNull(courseSimpleInfoDTO)) {
                vo.setCourseName(courseSimpleInfoDTO.getName());
                vo.setSections(courseSimpleInfoDTO.getSectionNum());
            }
            // 4.4.3 每个课程本周已学习小节数量
            vo.setWeekLearnedSections(map.getOrDefault(record.getId(), 0));
            result.add(vo);
        }

        return planPageVO.pageInfo(page.getTotal(), page.getPages(), result);
    }
}

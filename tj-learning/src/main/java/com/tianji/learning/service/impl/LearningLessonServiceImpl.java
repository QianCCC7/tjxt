package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.enums.LessonStatus;
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
        log.debug("新增用户课表成功");
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
        // 3.1 获取课表中所有课程的 ID
        Set<Long> courseIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        // 3.2 根据所有课程 ID查询所有课程信息，用于封装 LearningLessonVO
        List<CourseSimpleInfoDTO> coursesInfoList = courseClient.getSimpleInfoList(courseIds);
        if (CollectionUtils.isEmpty(coursesInfoList)) {
            throw new BadRequestException("课程信息不存在！");
        }
        // 3.3 将课程集合处理为Map，方便下面封装数据时，通过课程ID获取到课程信息
        Map<Long, CourseSimpleInfoDTO> cMap = coursesInfoList.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
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

    /**
     * 查询正在学习的课程
     * @return
     */
    @Override
    public LearningLessonVO queryMyCurrentLesson() {
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
}

package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.pojo.LearningLesson;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;

import java.util.List;

/**
 * 学生课程表 服务类接口
 *
 * @author QianCCC
 * @since 2023-12-06 23:59
 */
public interface ILearningLessonService extends IService<LearningLesson> {

    void addUserLessons(Long userId, List<Long> courseIds);

    PageDTO<LearningLessonVO> queryMyLessons(PageQuery pageQuery);

    LearningLessonVO queryMyCurrentLesson();

    void removeUserLessons(Long userId, Long courseId);

    void deleteInvalidByCourseId(Long courseId);

    Long isLessonValid(Long courseId);

    LearningLessonVO queryLessonStatus(Long courseId);

    Integer countLearningLessonByCourse(Long courseId);

    LearningLesson queryLessonByUserIdAndCourseId(Long userId, Long courseId);

    void createLearningPlan(LearningPlanDTO planDTO);

    LearningPlanPageVO queryMyPlans(PageQuery pageQuery);
}

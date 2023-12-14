package com.tianji.learning.controller;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.service.ILearningLessonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 学生课程表 前端控制器
 *
 * @author QianCCC
 * @since 2023-12-06 23:59
 */
@RestController
@RequestMapping("/lessons")
@RequiredArgsConstructor
@ApiOperation("我的课表相关接口")
public class LearningLessonController {

    private final ILearningLessonService learningLessonService;

    @GetMapping("/page")
    @ApiOperation("分页查询我的课表")
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery pageQuery) {
        return learningLessonService.queryMyLessons(pageQuery);
    }

    @GetMapping("/now")
    @ApiOperation("查询正在学习的课程")
    public LearningLessonVO queryMyCurrentLesson() {
        return learningLessonService.queryMyCurrentLesson();
    }

    @DeleteMapping("/{courseId}")
    @ApiOperation("用户删除已失效的课程")
    public void deleteInvalidByCourseId(@PathVariable(value = "courseId") Long courseId) {
        learningLessonService.deleteInvalidByCourseId(courseId);
    }

    @GetMapping("/{courseId}/valid")
    @ApiOperation("校验当前用户是否可以学习当前课程，即当前课程是否有效")
    public Long isLessonValid(@PathVariable(value = "courseId") Long courseId){
        return learningLessonService.isLessonValid(courseId);
    }
}

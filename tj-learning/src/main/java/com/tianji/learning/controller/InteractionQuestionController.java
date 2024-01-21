package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.service.IInteractionQuestionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * <p>
 * 互动提问的问题表 前端控制器
 * </p>
 *
 * @author QianCCC
 * @since 2024-01-15
 */
@RestController
@RequestMapping("/questions")
@Api(tags = "互动问答相关接口")
@RequiredArgsConstructor
public class InteractionQuestionController {
    private final IInteractionQuestionService questionService;

    @ApiOperation("新增互动问题")
    @PostMapping
    public void saveQuestion(@Valid @RequestBody QuestionFormDTO questionFormDTO) {
        questionService.saveQuestion(questionFormDTO);
    }

    @ApiOperation("修改互动问题")
    @PutMapping("/{id}")
    public void updateQuestion(@PathVariable("id") Long id,
                               @RequestBody QuestionFormDTO questionFormDTO) {
        questionService.updateQuestion(id, questionFormDTO);
    }

    @ApiOperation("删除互动问题")
    @DeleteMapping("/{id}")
    public void deleteQuestion(@PathVariable("id") Long id) {
        questionService.deleteQuestion(id);
    }

    @ApiOperation("分页查询互动问题")
    @GetMapping("/page")
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery pageQuery) {
        return questionService.queryQuestionPage(pageQuery);
    }

    @ApiOperation("根据问题 id查询指定问题详情")
    @GetMapping("/{id}")
    public QuestionVO queryQuestionById(@PathVariable("id") Long id) {
        return questionService.queryQuestionById(id);
    }
}

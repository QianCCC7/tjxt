package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.service.IInteractionQuestionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


/**
 * <p>
 * 互动提问的问题表 前端控制器
 * </p>
 *
 * @author QianCCC
 * @since 2024-01-15
 */
@RestController
@RequestMapping("/admin/questions")
@Api(tags = "管理端互动问答相关接口")
@RequiredArgsConstructor
public class InteractionQuestionAdminController {
    private final IInteractionQuestionService questionService;

    @ApiOperation("管理端分页查询互动问题")
    @GetMapping("/page")
    public PageDTO<QuestionAdminVO> queryQuestionPageAdmin(QuestionAdminPageQuery pageQuery) {
        return questionService.queryQuestionPageAdmin(pageQuery);
    }

    @ApiOperation("管理端根据问题 id查询指定问题详情")
    @GetMapping("/{id}")
    public QuestionAdminVO  queryQuestionByIdAdmin(@PathVariable("id") Long id) {
        return questionService.queryQuestionByIdAdmin(id);
    }

    @ApiOperation("管理端隐藏或显示指定问题")
    @PutMapping("/{id}/hidden/{hidden}")
    public void hiddenQuestion(@PathVariable("id") Long id,
                               @PathVariable("hidden") Boolean hidden) {
        questionService.hiddenQuestion(id, hidden);
    }
}

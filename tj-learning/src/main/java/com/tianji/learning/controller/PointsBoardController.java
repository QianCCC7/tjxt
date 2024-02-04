package com.tianji.learning.controller;


import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardVO;
import com.tianji.learning.service.IPointsBoardService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 学霸天梯榜 前端控制器
 * </p>
 *
 * @author QianCCC
 * @since 2024-01-28
 */
@RestController
@Api(tags = "学霸天梯榜相关接口")
@RequestMapping("/boards")
@RequiredArgsConstructor
public class PointsBoardController {
    private final IPointsBoardService pointsBoardService;

    @ApiOperation("分页查询指定赛季的学霸积分排行榜")
    @GetMapping
    public PointsBoardVO queryPointsBoardBySeason(PointsBoardQuery query) {
        return pointsBoardService.queryPointsBoardBySeason(query);
    }
}

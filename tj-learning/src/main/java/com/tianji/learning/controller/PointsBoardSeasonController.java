package com.tianji.learning.controller;


import com.tianji.learning.domain.vo.PointsBoardSeasonVO;
import com.tianji.learning.service.IPointsBoardSeasonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 赛季表 前端控制器
 * </p>
 *
 * @author QianCCC
 * @since 2024-01-28
 */
@RestController
@RequestMapping("/boards")
@Api(tags = "赛季的相关接口")
@RequiredArgsConstructor
public class PointsBoardSeasonController {
    private final IPointsBoardSeasonService pointsBoardSeasonService;

    @ApiOperation("查询历史赛季列表")
    @GetMapping("/seasons/list")
    public List<PointsBoardSeasonVO> queryPointsBoardSeasons() {
        return pointsBoardSeasonService.queryPointsBoardSeasons();
    }
}

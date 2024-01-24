package com.tianji.remark.controller;


import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.service.ILikedRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * <p>
 * 点赞记录表 前端控制器
 * </p>
 *
 * @author QianCCC
 * @since 2024-01-23
 */
@RestController
@RequestMapping("/likes")
@RequiredArgsConstructor
@Api(tags = "点赞业务相关接口")
public class LikedRecordController {
    private final ILikedRecordService recordService;

    @PostMapping
    @ApiOperation("用户点赞或者取消点赞")
    public void addLikeRecord(@Valid @RequestBody LikeRecordFormDTO likeRecordFormDTO) {
        recordService.addLikeRecord(likeRecordFormDTO);
    }
}

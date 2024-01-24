package com.tianji.remark.controller;


import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.service.ILikedRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Set;

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

    @ApiOperation("查询业务 id集合中每个的点赞状态")
    @GetMapping("/list")
    public Set<Long> isBizLiked(@RequestParam("bizIds") List<Long> bizIds) {
        return recordService.isBizLiked(bizIds);
    }
}

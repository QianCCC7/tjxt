package com.tianji.learning.controller;

import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.service.ISignRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("sign-records")
@Api(tags = "签到功能相关接口")
@RequiredArgsConstructor
public class SignRecordController {
    private final ISignRecordService recordService;

    @ApiOperation("保存签到记录")
    @PostMapping
    public SignResultVO addSignRecords() {
        return recordService.addSignRecords();
    }

    @ApiOperation("查询本月签到记录")
    @GetMapping
    public Byte[] querySignRecords() {
        return recordService.querySignRecords();
    }
}

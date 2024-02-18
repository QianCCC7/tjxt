package com.tianji.promotion.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.query.CodeQuery;
import com.tianji.promotion.domain.vo.ExchangeCodeVO;
import com.tianji.promotion.service.IExchangeCodeService;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * <p>
 * 兑换码 前端控制器
 * </p>
 *
 * @author QianCCC
 * @since 2024-02-07
 */
@RestController
@RequestMapping("/codes")
@RequiredArgsConstructor
public class ExchangeCodeController {
    private final IExchangeCodeService exchangeCodeService;

    @ApiOperation("分页查询兑换码")
    @GetMapping("/page")
    public PageDTO<ExchangeCodeVO> queryExchangeCodePage(@Valid CodeQuery codeQuery) {
        return exchangeCodeService.queryExchangeCodePage(codeQuery);
    }
}

package com.tianji.promotion.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.service.ICouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * <p>
 * 优惠券的规则信息 前端控制器
 * </p>
 *
 * @author QianCCC
 * @since 2024-02-07
 */
@RestController
@RequestMapping("/coupons")
@Api(tags = "优惠券相关接口")
@RequiredArgsConstructor
public class CouponController {
    private final ICouponService couponService;

    @ApiOperation("新增优惠券")
    @PostMapping
    public void saveCoupon(@RequestBody @Valid CouponFormDTO couponFormDTO) {
        couponService.saveCoupon(couponFormDTO);
    }

    @ApiOperation("分页查询优惠券")
    @GetMapping("/page")
    public PageDTO<CouponPageVO> queryCouponByPage(CouponQuery query) {
        return couponService.queryCouponByPage(query);
    }
}

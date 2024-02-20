package com.tianji.promotion.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.query.UserCouponQuery;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.service.IUserCouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 前端控制器
 * </p>
 *
 * @author QianCCC
 * @since 2024-02-18
 */
@RestController
@RequestMapping("/user-coupons")
@Api(tags = "优惠券相关接口")
@RequiredArgsConstructor
public class UserCouponController {
    private final IUserCouponService userCouponService;

    @ApiOperation("用户领取优惠券")
    @PostMapping("/{id}/receive")
    public void receiveCoupon(@PathVariable("id") Long couponId) {
        userCouponService.receiveCoupon(couponId);
    }

    @ApiOperation("用户通过兑换码兑换优惠券")
    @PostMapping("/{code}/exchange")
    public void receiveCouponByExchangeCode(@PathVariable("code") String code) {
        userCouponService.receiveCouponByExchangeCode(code);
    }

    @ApiOperation("分页查询我的优惠券")
    @GetMapping("/page")
    public PageDTO<CouponVO> queryMyCoupon(UserCouponQuery query) {
        return userCouponService.queryMyCoupon(query);
    }
}

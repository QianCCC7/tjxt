package com.tianji.promotion.controller;


import com.tianji.api.promotion.CouponDiscountDTO;
import com.tianji.api.promotion.OrderCourseDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.api.promotion.OrderCouponDTO;
import com.tianji.promotion.domain.query.UserCouponQuery;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.service.IDiscountService;
import com.tianji.promotion.service.IUserCouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    private final IDiscountService discountService;

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

    @ApiOperation("查询我的优惠券可用方案")
    @PostMapping("/available")
    public List<CouponDiscountDTO> findDiscountSolution(@RequestBody List<OrderCourseDTO> orderCourseDTOList) {
        return discountService.findDiscountSolution(orderCourseDTOList);
    }

    @ApiOperation("根据券方案计算订单优惠明细")
    @PostMapping("/discount")
    public CouponDiscountDTO queryDiscountDetailByOrder(@RequestBody OrderCouponDTO couponDTO) {
        return discountService.queryDiscountDetailByOrder(couponDTO);
    }

    @ApiOperation("核销指定优惠券")
    @PutMapping("/use")
    public void writeOffCoupon(@RequestParam("couponIds") List<Long> userCouponIds) {
        userCouponService.writeOffCoupon(userCouponIds);
    }

    @ApiOperation("退还优惠券")
    @PutMapping("/refund")
    public void refundCoupon(@RequestParam("couponIds") List<Long> userCouponIds) {
        userCouponService.refundCoupon(userCouponIds);
    }

    @ApiOperation("查询已使用的优惠券规则信息")
    @GetMapping("/rules")
    public List<String> queryDiscountRules(@RequestParam("couponIds") List<Long> userCouponIds) {
        return userCouponService.queryDiscountRules(userCouponIds);
    }
}

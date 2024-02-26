package com.tianji.api.client.promotion;

import com.tianji.api.client.promotion.fallback.PromotionClientFallback;
import com.tianji.api.promotion.CouponDiscountDTO;
import com.tianji.api.promotion.OrderCouponDTO;
import com.tianji.api.promotion.OrderCourseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(value = "promotion-service", fallbackFactory = PromotionClientFallback.class)
public interface PromotionClient {

    @PostMapping("/user-coupons/available")
    List<CouponDiscountDTO> findDiscountSolution(@RequestBody List<OrderCourseDTO> orderCourseDTOList);

    @PostMapping("/user-coupons/discount")
    CouponDiscountDTO queryDiscountDetailByOrder(@RequestBody OrderCouponDTO couponDTO);

    @PutMapping("/user-coupons/use")
    void writeOffCoupon(@RequestParam("couponIds") List<Long> userCouponIds);

    @PutMapping("/user-coupons/refund")
    void refundCoupon(@RequestParam("couponIds") List<Long> userCouponIds);

    @GetMapping("/rules")
    List<String> queryDiscountRules(@RequestParam("couponIds") List<Long> userCouponIds);
}

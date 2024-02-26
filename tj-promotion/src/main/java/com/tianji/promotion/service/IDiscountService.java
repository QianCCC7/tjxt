package com.tianji.promotion.service;

import com.tianji.api.promotion.CouponDiscountDTO;
import com.tianji.api.promotion.OrderCourseDTO;
import com.tianji.api.promotion.OrderCouponDTO;

import java.util.List;

public interface IDiscountService {
    List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> orderCourseDTOList);

    CouponDiscountDTO queryDiscountDetailByOrder(OrderCouponDTO couponDTO);
}

package com.tianji.promotion.service;

import com.tianji.api.promotion.CouponDiscountDTO;
import com.tianji.api.promotion.OrderCourseDTO;

import java.util.List;

public interface IDiscountService {
    List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> orderCourseDTOList);
}

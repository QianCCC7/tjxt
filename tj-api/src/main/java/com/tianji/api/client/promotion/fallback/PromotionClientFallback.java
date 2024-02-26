package com.tianji.api.client.promotion.fallback;

import com.tianji.api.client.promotion.PromotionClient;
import com.tianji.api.promotion.CouponDiscountDTO;
import com.tianji.api.promotion.OrderCouponDTO;
import com.tianji.api.promotion.OrderCourseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

import java.util.Collections;
import java.util.List;

@Slf4j
public class PromotionClientFallback implements FallbackFactory<PromotionClient> {

    @Override
    public PromotionClient create(Throwable cause) {
        log.error("查询促销服务异常", cause);
        return new PromotionClient() {
            @Override
            public List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> orderCourseDTOList) {
                return Collections.emptyList();
            }

            @Override
            public CouponDiscountDTO queryDiscountDetailByOrder(OrderCouponDTO couponDTO) {
                return null;
            }

        };
    }
}

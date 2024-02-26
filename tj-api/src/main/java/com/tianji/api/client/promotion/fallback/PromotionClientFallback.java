package com.tianji.api.client.promotion.fallback;

import com.tianji.api.client.promotion.PromotionClient;
import com.tianji.api.promotion.CouponDiscountDTO;
import com.tianji.api.promotion.OrderCouponDTO;
import com.tianji.api.promotion.OrderCourseDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.CollUtils;
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

            @Override
            public void writeOffCoupon(List<Long> userCouponIds) {
                throw new BizIllegalException(500, "核销优惠券异常", cause);
            }

            @Override
            public void refundCoupon(List<Long> userCouponIds) {
                throw new BizIllegalException(500, "退还优惠券异常", cause);
            }

            @Override
            public List<String> queryDiscountRules(List<Long> userCouponIds) {
                return CollUtils.emptyList();
            }

        };
    }
}

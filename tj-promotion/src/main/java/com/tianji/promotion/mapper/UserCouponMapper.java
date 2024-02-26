package com.tianji.promotion.mapper;

import com.tianji.promotion.domain.pojo.Coupon;
import com.tianji.promotion.domain.pojo.UserCoupon;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.promotion.enums.UserCouponStatus;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 Mapper 接口
 * </p>
 *
 * @author QianCCC
 * @since 2024-02-18
 */
public interface UserCouponMapper extends BaseMapper<UserCoupon> {

    List<Coupon> queryMyCoupon(@Param("userId") Long userId);

    List<Coupon> queryCouponsByUserCouponIds(@Param("userCouponIds") List<Long> userCouponIds,
                                             @Param("status") UserCouponStatus unused);
}

package com.tianji.promotion.service;

import com.tianji.promotion.domain.pojo.UserCoupon;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务类
 * </p>
 *
 * @author QianCCC
 * @since 2024-02-18
 */
public interface IUserCouponService extends IService<UserCoupon> {

    void receiveCoupon(Long couponId);
}

package com.tianji.promotion.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.pojo.Coupon;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponDetailVO;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponVO;

import java.util.List;

/**
 * <p>
 * 优惠券的规则信息 服务类
 * </p>
 *
 * @author QianCCC
 * @since 2024-02-07
 */
public interface ICouponService extends IService<Coupon> {

    void saveCoupon(CouponFormDTO couponFormDTO);

    PageDTO<CouponPageVO> queryCouponByPage(CouponQuery query);

    void grantCoupon(CouponIssueFormDTO couponIssueFormDTO);

    void updateCoupon(CouponFormDTO couponFormDTO);

    void deleteCouponById(Long id);

    CouponDetailVO queryCouponDetailById(Long id);

    void beginIssueCouponBatch(List<Coupon> records);

    void pauseIssueCoupon(Long id);

    void finishIssueCouponBatch(List<Coupon> records);

    List<CouponVO> queryIssuingCoupon();
}

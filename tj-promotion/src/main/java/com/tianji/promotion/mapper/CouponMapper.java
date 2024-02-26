package com.tianji.promotion.mapper;

import com.tianji.promotion.domain.pojo.Coupon;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * <p>
 * 优惠券的规则信息 Mapper 接口
 * </p>
 *
 * @author QianCCC
 * @since 2024-02-07
 */
public interface CouponMapper extends BaseMapper<Coupon> {

    @Update("update coupon set issue_num = issue_num + 1 where id = #{couponId} and issue_num < total_num")
    int incrIssueNum(@Param("couponId") Long couponId);

    int incrUserNum(@Param("userCouponIds") List<Long> userCouponIdList,
                    @Param("count") int count);
}

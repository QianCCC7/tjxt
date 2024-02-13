package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.pojo.Coupon;
import com.tianji.promotion.domain.pojo.CouponScope;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.ICouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 优惠券的规则信息 服务实现类
 * </p>
 *
 * @author QianCCC
 * @since 2024-02-07
 */
@Service
@RequiredArgsConstructor
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {
    private final ICouponScopeService couponScopeService;

    /**
     * 新增优惠券
     */
    @Override
    @Transactional
    public void saveCoupon(CouponFormDTO couponFormDTO) {
        // 1. 保存优惠券信息
        Coupon coupon = BeanUtils.copyBean(couponFormDTO, Coupon.class);
        save(coupon);
        // 2. 保存优惠券范围信息
        if (!couponFormDTO.getSpecific()) {
            return;// 没有限定范围
        }
        List<Long> scopes = couponFormDTO.getScopes();
        if (CollUtils.isEmpty(scopes)) {
            throw new BadRequestException("限定范围不能为空！");
        }
        List<CouponScope> couponScopeList = scopes.stream().map(bizId -> new CouponScope()
                        .setBizId(bizId)
                        .setCouponId(coupon.getId()))
                .collect(Collectors.toList());
        couponScopeService.saveBatch(couponScopeList);
    }

    /**
     * 分页查询优惠券
     */
    @Override
    public PageDTO<CouponPageVO> queryCouponByPage(CouponQuery query) {
        // 1. 查询数据
        Page<Coupon> page = lambdaQuery()
                .eq(Objects.nonNull(query.getType()), Coupon::getDiscountType, query.getType())
                .eq(Objects.nonNull(query.getStatus()), Coupon::getStatus, query.getStatus())
                .like(StringUtils.isNotBlank(query.getName()), Coupon::getName, query.getName())
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        // 2. 封装vo
        List<Coupon> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        List<CouponPageVO> couponPageVOList = BeanUtils.copyList(records, CouponPageVO.class);
        // 3. 返回数据
        return PageDTO.of(page, couponPageVOList);
    }
}

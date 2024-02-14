package com.tianji.promotion.handler;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.utils.CollUtils;
import com.tianji.promotion.domain.pojo.Coupon;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.service.ICouponService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CouponIssueHandler {
    private final ICouponService couponService;

    /**
     * 处理定时发布优惠券的任务
     */
    @XxlJob("couponIssueJob") // 指定任务名称
    public void handleCouponIssueJob() {
        // 1. 任务分片
        int index = XxlJobHelper.getShardIndex() + 1;
        int size = Integer.parseInt(XxlJobHelper.getJobParam());// 获取页码,20
        // 1.1 只要找到那些处于未开始的，并且发放时间早于当前时间的即可。
        Page<Coupon> page = couponService.lambdaQuery()
                .eq(Coupon::getStatus, CouponStatus.UN_ISSUE)
                .le(Coupon::getIssueBeginTime, LocalDateTime.now())
                .page(new Page<>(index, size));
        List<Coupon> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return;
        }
        // 2. 数据库更新优惠券发布状态
        couponService.beginIssueCouponBatch(records);
    }

    /**
     * 处理定时结束优惠券的任务
     */
    @XxlJob("couponFinishJob") // 指定任务名称
    public void handleCouponFinishJob() {
        // 1. 任务分片
        int index = XxlJobHelper.getShardIndex() + 1;
        int size = Integer.parseInt(XxlJobHelper.getJobParam());// 获取页码,20
        // 1.1 只要找到那些发放结束的，并且结束时间早于当前时间的即可。
        Page<Coupon> page = couponService.lambdaQuery()
                .eq(Coupon::getStatus, CouponStatus.FINISHED)
                .le(Coupon::getIssueEndTime, LocalDateTime.now())
                .page(new Page<>(index, size));
        List<Coupon> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return;
        }
        // 2. 数据库更新优惠券发布状态
        couponService.finishIssueCouponBatch(records);
    }
}

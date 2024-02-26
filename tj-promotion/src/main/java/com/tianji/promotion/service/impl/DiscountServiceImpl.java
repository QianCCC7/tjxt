package com.tianji.promotion.service.impl;

import com.tianji.api.promotion.CouponDiscountDTO;
import com.tianji.api.promotion.OrderCourseDTO;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.api.promotion.OrderCouponDTO;
import com.tianji.promotion.domain.pojo.Coupon;
import com.tianji.promotion.domain.pojo.CouponScope;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.IDiscountService;
import com.tianji.promotion.strategy.discount.Discount;
import com.tianji.promotion.strategy.discount.DiscountStrategy;
import com.tianji.promotion.utils.PermuteUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscountServiceImpl implements IDiscountService {
    private final UserCouponMapper userCouponMapper;
    private final ICouponScopeService couponScopeService;
    private final Executor calculateDiscountExecutor;

    /**
     * 查询我的优惠券可用方案
     * @param orderCourseDTOList 用户已购买的所有课程信息
     * @return 用户可用的优惠券信息以及本单最大优惠金额
     */
    @Override
    public List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> orderCourseDTOList) {
        // 1. 查询当前用户所有可用优惠券(1. 必须属于当前用户券 2. 状态必须是未使用)
        Long userId = UserContext.getUser();
        // 由于需要查询到折扣信息，所以需要联合查询，其中 Coupon的 creater属性作为 UserCoupon的 id属性
        List<Coupon> couponList = userCouponMapper.queryMyCoupon(userId);
        if (CollUtils.isEmpty(couponList)) {
            return CollUtils.emptyList();
        }
        // 2. 初筛，判断商品总价是否满足使用优惠券的门槛
        // 2.1 计算订单总价
        int total = orderCourseDTOList.stream()
                .mapToInt(OrderCourseDTO::getPrice)
                .sum();
        // 2.2 筛选可用优惠券(用于简化细筛)
        List<Coupon> availableCouponList = couponList.stream()
                .filter(coupon -> DiscountStrategy.getDiscount(coupon.getDiscountType()).canUse(total, coupon))
                .collect(Collectors.toList());
        if (CollUtils.isEmpty(availableCouponList)) {
            return CollUtils.emptyList();
        }
        // 3. 对优惠券做全排列组合(使用顺序不同，最终的优惠价格可能也不同)
        // 3.1 细筛(每个优惠券的都有自己的使用范围（指定的课程分类），找出每个优惠券可用的课程，判断课程是否可以使用优惠券)
        Map<Coupon, List<OrderCourseDTO>> availableCouponMap = findAvailableCoupon(availableCouponList, orderCourseDTOList);
        if (CollUtils.isEmpty(availableCouponMap)) {
            return CollUtils.emptyList();
        }
        // 3.2 排列组合
        availableCouponList = new ArrayList<>(availableCouponMap.keySet());
        List<List<Coupon>> solutions = PermuteUtil.permute(availableCouponList);
        // 3.3 注意：添加只选一张券的方案
        for (Coupon coupon : availableCouponList) {
            solutions.add(List.of(coupon));
        }
        // 4. 并发计算组合中的最优方案
        // 4.1 定义闭锁
        CountDownLatch countDownLatch = new CountDownLatch(solutions.size());
        List<CouponDiscountDTO> list = Collections.synchronizedList(new ArrayList<>(solutions.size()));
        // 枚举每种组合
        for (List<Coupon> solution : solutions) {
            // 4.2 异步计算每种方案组合的优惠明细
            CompletableFuture
                    .supplyAsync(() -> calculateDiscountSolution(availableCouponMap, orderCourseDTOList, solution),
                            calculateDiscountExecutor)
                            .thenAccept(dto -> {
                                list.add(dto);// 接收返回结果
                                countDownLatch.countDown();// 提交任务
                            });
            list.add(calculateDiscountSolution(availableCouponMap, orderCourseDTOList, solution));
        }
        // 4.3 等待运算结果
        try {
            countDownLatch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error("优惠方案计算被中断，{}", e.getMessage());
        }
        // 5. 筛选最优解
        // 5.1 用券相同时，优惠金额最高的方案
        // 5.2 优惠金额相同时，用券最少的方案
        return findBestSolution(list);
    }

    /**
     * 细筛(每个优惠券的都有自己的使用范围（指定的课程分类），找出每个优惠券可用的所有课程，判断课程是否可以使用优惠券)
     */
    private Map<Coupon, List<OrderCourseDTO>> findAvailableCoupon(List<Coupon> couponList, List<OrderCourseDTO> orderCourseDTOList) {
        Map<Coupon, List<OrderCourseDTO>> availableCouponMap = new HashMap<>(couponList.size());
        for (Coupon coupon : couponList) {
            // 1. 从所有传入的课程中找出当前遍历到的优惠券可用的课程
            List<OrderCourseDTO> availableCourseList = orderCourseDTOList;
            if (coupon.getSpecific()) {
                // 1.1 限定了范围，查询券的范围，如果没有限定范围，那么对于传入的所有课程都可使用
                List<CouponScope> couponScopeList = couponScopeService.lambdaQuery().eq(CouponScope::getCouponId, coupon.getId()).list();
                // 1.2 获取券的范围内对应的分类 id
                Set<Long> bizIds = couponScopeList.stream().map(CouponScope::getBizId).collect(Collectors.toSet());
                // 1.3 筛选可用课程
                availableCourseList = orderCourseDTOList.stream().filter(course -> bizIds.contains(course.getCateId())).collect(Collectors.toList());
            }
            if (CollUtils.isEmpty(availableCourseList)) {
                continue;
            }
            // 2. 计算课程总价
            int total = availableCourseList.stream().mapToInt(OrderCourseDTO::getPrice).sum();
            // 3. 课程是否可以使用优惠券
            boolean canUse = DiscountStrategy.getDiscount(coupon.getDiscountType()).canUse(total, coupon);
            if (canUse) {
                availableCouponMap.put(coupon, availableCourseList);
            }
        }

        return availableCouponMap;
    }

    /**
     * 并发计算组合中的最优方案
     */
    private CouponDiscountDTO calculateDiscountSolution(Map<Coupon, List<OrderCourseDTO>> availableCouponMap,
                                                        List<OrderCourseDTO> orderCourseDTOList, List<Coupon> solution) {
        // 1. 初始化 dto
        CouponDiscountDTO dto = new CouponDiscountDTO();
        // 2. 初始化优惠明细 Map，key为课程 id，value为课程对应的已优惠明细(初始化已优惠 0元)
        Map<Long, Integer> detailMap = orderCourseDTOList.stream().collect(Collectors.toMap(OrderCourseDTO::getId, ocd -> 0));
        // 3. 计算折扣
        for (Coupon coupon : solution) {
            // 3.1 获取优惠券限定范围内的所有课程
            List<OrderCourseDTO> availableCourses = availableCouponMap.get(coupon);
            // 3.2 计算课程总价(注意要将在其他优惠券下已优惠的部分减去)
            int total = availableCourses.stream().mapToInt(ocd -> Math.max(ocd.getPrice() - detailMap.get(ocd.getId()), 0)).sum();
            // 3.3 判断是否可用
            Discount discount = DiscountStrategy.getDiscount(coupon.getDiscountType());
            boolean canUse = discount.canUse(total, coupon);
            if (!canUse) {
                continue;
            }
            // 3.4 计算优惠
            int discountAmount = discount.calculateDiscount(total, coupon);
            // 3.5 计算优惠明细，即每个课程优惠了多少
            calculateDiscountDetail(detailMap, availableCourses, total, discountAmount);
            // 3.6 更新 dto数据
            dto.getIds().add(coupon.getId());
            dto.getRules().add(discount.getRule(coupon));
            dto.setDiscountAmount(discountAmount + dto.getDiscountAmount());
            dto.setDiscountDetail(detailMap);
        }
        return dto;
    }

    /**
     * 计算优惠明细，即每个课程优惠了多少
     */
    private void calculateDiscountDetail(Map<Long, Integer> detailMap, List<OrderCourseDTO> availableCourses,
                                         int total, int discountAmount) {
        int remainDiscount = discountAmount;// 剩余未优惠的金额
        for (int i = 0; i < availableCourses.size(); i++) {
            OrderCourseDTO course = availableCourses.get(i);
            int discount;
            // 1. 判断是否是最后一个课程
            if (i == availableCourses.size() - 1) {
                // 1.1 如果是最后一个课程，为了避免出现精度损失导致的金额不一致，最后一个商品的优惠明细等于优惠总金额减去其它商品的优惠明细之和
                discount = remainDiscount;
            } else {
                // 1.2 不是最后一个课程，课程的优惠明细等于课程价格在总价中占的比例再乘以总的折扣，这样可以让每门课程尽可能分得同等的折扣比例
                discount = course.getPrice() / total * discountAmount;
                remainDiscount -= discount;
            }
            // 2. 更新当前课程优惠明细
            detailMap.put(course.getId(), detailMap.getOrDefault(course.getId(), 0) + discount);
        }
    }

    /**
     * 计算最优解
     */
    private List<CouponDiscountDTO> findBestSolution(List<CouponDiscountDTO> list) {
        // 1. 准备map，存储最优解
        Map<String, CouponDiscountDTO> moreDiscountMap = new HashMap<>();
        Map<Integer, CouponDiscountDTO> lessCouponMap = new HashMap<>();
        // 2. 遍历，查找最优解
        for (CouponDiscountDTO solution : list) {
            // 2.1 计算 id组合
            String ids = solution.getIds().stream()
                    .sorted(Long::compare)
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            // 2.2 比较用券相同时，优惠金额是否最大
            CouponDiscountDTO bestSolution = moreDiscountMap.get(ids);
            if (Objects.nonNull(bestSolution) && bestSolution.getDiscountAmount() > solution.getDiscountAmount()) {
                // 当前优惠券金额少，跳过
                continue;
            }
            // 2.3 金额相同时，是否使用的券最少
            bestSolution = lessCouponMap.get(solution.getDiscountAmount());
            int size = solution.getIds().size();// 存储单券
            if (size > 1 && Objects.nonNull(bestSolution) && bestSolution.getIds().size() <= size) {
                // 当前方案消耗更多券，跳过
                continue;
            }
            // 2.4 更新最优解
            moreDiscountMap.put(ids, solution);
            lessCouponMap.put(solution.getDiscountAmount(), solution);
        }
        // 3. 求交集
        Collection<CouponDiscountDTO> intersection = CollUtils.intersection(moreDiscountMap.values(), lessCouponMap.values());
        // 4. 排序（按优惠金额降序）
        return intersection.stream()
                .sorted(Comparator.comparingInt(CouponDiscountDTO::getDiscountAmount).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 根据券方案计算订单优惠明细
     */
    @Override
    public CouponDiscountDTO queryDiscountDetailByOrder(OrderCouponDTO orderCouponDTO) {
        // 1. 查询用户券信息
        List<Long> userCouponIds = orderCouponDTO.getUserCouponIds();
        List<Coupon> couponList = userCouponMapper.queryCouponsByUserCouponIds(userCouponIds, UserCouponStatus.UNUSED);
        if (CollUtils.isEmpty(couponList)) {
            return null;
        }
        // 2. 找出每个优惠券可用的所有课程
        Map<Coupon, List<OrderCourseDTO>> availableCouponMap = findAvailableCoupon(couponList, orderCouponDTO.getCourseList());
        if (CollUtils.isEmpty(availableCouponMap)) {
            return null;
        }
        // 3. 查询优惠规则
        return calculateDiscountSolution(availableCouponMap, orderCouponDTO.getCourseList(), couponList);
    }
}

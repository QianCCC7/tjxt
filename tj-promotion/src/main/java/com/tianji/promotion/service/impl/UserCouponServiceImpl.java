package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.constants.RedisConstants;
import com.tianji.promotion.domain.pojo.Coupon;
import com.tianji.promotion.domain.pojo.ExchangeCode;
import com.tianji.promotion.domain.pojo.UserCoupon;
import com.tianji.promotion.domain.query.UserCouponQuery;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.utils.CodeUtil;
import com.tianji.promotion.utils.MyRedisLock;
import com.tianji.promotion.utils.RedisLock;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
 * </p>
 *
 * @author QianCCC
 * @since 2024-02-18
 */
@Service
@RequiredArgsConstructor
public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {
    private final CouponMapper couponMapper;
    private final IExchangeCodeService exchangeCodeService;
    private final RedissonClient redissonClient;

    /**
     * 用户领取优惠券
     */
    @Override
    public void receiveCoupon(Long couponId) {
        // 1. 查询优惠券
        Coupon coupon = couponMapper.selectById(couponId);
        if (Objects.isNull(coupon)) {
            throw new BadRequestException("优惠券不存在");
        }
        // 2. 校验发放时间
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getIssueBeginTime()) || now.isAfter(coupon.getIssueEndTime())) {
            throw new BadRequestException("优惠券发放结束或尚未开始");
        }
        // 3. 校验库存
        if (coupon.getIssueNum() >= coupon.getTotalNum()) {
            throw new BadRequestException("优惠券库存不足");
        }
        // 4. 校验并且创建用户券
        Long userId = UserContext.getUser();
        IUserCouponService userCouponService = (IUserCouponService) AopContext.currentProxy();
        userCouponService.checkAndCreateUserCoupon(coupon, userId);
    }

    @MyRedisLock(name = "lock:coupon:uid:#{userId}")
    @Transactional
    @Override
    public void checkAndCreateUserCoupon(Coupon coupon, Long userId) {
        // 注意这里要转为字符串，因为 userId可能是变量，转为字符串变为常量
        // 1. 校验是否超过限领数
        // 1.1 统计当前用户对当前优惠券已经领取的数量
        Integer count = lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, coupon.getId())
                .count();
        // 1.2 判断是否超限
        if (Objects.nonNull(count) && count >= coupon.getUserLimit()) {
            throw new BadRequestException("已超过领取该优惠券上限");
        }
        // 2. 更新优惠券发放数量+1
        int sucRow = couponMapper.incrIssueNum(coupon.getId());
        if (sucRow == 0) {
            throw new BizIllegalException("优惠券库存不足");
        }
        // 3. 写入数据库
        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(userId);
        userCoupon.setCouponId(coupon.getId());
        // 3.1 设置优惠券有效期
        LocalDateTime termBeginTime = coupon.getTermBeginTime();
        LocalDateTime termEndTime = coupon.getTermEndTime();
        if (Objects.isNull(termBeginTime)) {// 为空则表示有效期为天数
            termBeginTime = LocalDateTime.now();
            termEndTime = termBeginTime.plusDays(coupon.getTermDays());
        }
        userCoupon.setTermBeginTime(termBeginTime);
        userCoupon.setTermEndTime(termEndTime);
        save(userCoupon);
    }

    /**
     * 用户通过兑换码兑换优惠券
     */
    @Override
    @Transactional
    public void receiveCouponByExchangeCode(String code) {
        // 1. 校验并解析兑换码，解析出每个兑换码的唯一序列号
        long serialNum = CodeUtil.parseCode(code);
        // 2. 校验是否已经兑换
        // 逻辑：无论是否兑换过都将其标记为 true，exchanged返回的是标记前的值
        // 如果在标记前就为 true说明已被兑换，则抛出异常
        boolean exchanged = exchangeCodeService.updateExchangeCodeMark(serialNum, true);
        if (exchanged) {
            throw new BizIllegalException("兑换码已被兑换");
        }
        try {
            // 3. 查询兑换码
            ExchangeCode exchangeCode = exchangeCodeService.getById(serialNum);
            // 4. 是否存在
            if (Objects.isNull(exchangeCode)) {
                throw new BizIllegalException("兑换码不存在");
            }
            // 5. 校验并且创建用户券
            LocalDateTime now = LocalDateTime.now();
            if (exchangeCode.getExpiredTime().isBefore(now)) {
                throw new BizIllegalException("兑换码已过期");
            }
            Coupon coupon = couponMapper.selectById(exchangeCode.getExchangeTargetId());
            Long userId = UserContext.getUser();
            synchronized (userId.toString().intern()) {
                IUserCouponService userCouponService = (IUserCouponService) AopContext.currentProxy();
                userCouponService.checkAndCreateUserCoupon(coupon, userId);
            }
            // 6. 更新兑换码状态(mysql+redis)
            exchangeCodeService.lambdaUpdate()
                    .set(ExchangeCode::getUserId, userId)
                    .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
                    .eq(ExchangeCode::getId, serialNum)
                    .update();
        } catch (Exception e) {
            // 出现异常，将兑换标记标志位 false，因为在上面exchangeCodeService方法中标记为 true了
            exchangeCodeService.updateExchangeCodeMark(serialNum, false);
            throw e;
        }
    }

    /**
     * 查询我的优惠券
     */
    @Override
    public PageDTO<CouponVO> queryMyCoupon(UserCouponQuery query) {
        // 1. 获取当前用户
        Long userId = UserContext.getUser();
        // 2. 获取当前用户的用户券
        Page<UserCoupon> page = lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getStatus, query.getStatus())
                .page(query.toMpPage(new OrderItem("term_end_time", true)));
        List<UserCoupon> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        // 3. 封装 vo
        Set<Long> set = records.stream().map(UserCoupon::getCouponId).collect(Collectors.toSet());
        List<Coupon> couponList = couponMapper.selectBatchIds(set);
        List<CouponVO> couponVOList = BeanUtils.copyList(couponList, CouponVO.class);
        return PageDTO.of(page, couponVOList);
    }
}

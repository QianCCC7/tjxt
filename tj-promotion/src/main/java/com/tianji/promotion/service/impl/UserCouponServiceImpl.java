package com.tianji.promotion.service.impl;

import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.autoconfigure.redisson.annotations.Lock;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.constants.RedisConstants;
import com.tianji.promotion.domain.dto.UserCouponDTO;
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
import java.util.Map;
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
    private final StringRedisTemplate redisTemplate;
    private final RabbitMqHelper mqHelper;

    /**
     * 用户领取优惠券
     */
    @MyRedisLock(name = "lock:coupon:#{couponId}")
    @Override
    public void receiveCoupon(Long couponId) {
        // 1. 查询优惠券
        // Coupon coupon = couponMapper.selectById(couponId);
        Coupon coupon = queryCouponByCache(couponId);
        if (Objects.isNull(coupon)) {
            throw new BadRequestException("优惠券不存在");
        }
        // 2. 校验发放时间
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getIssueBeginTime()) || now.isAfter(coupon.getIssueEndTime())) {
            throw new BadRequestException("优惠券发放结束或尚未开始");
        }
        // 3. 校验库存
        // if (coupon.getIssueNum() >= coupon.getTotalNum()) {
        if (coupon.getTotalNum() <= 0) {// redis中存储的totalNum为剩余库存
            throw new BadRequestException("优惠券库存不足");
        }
        // 4. 校验并且创建用户券
        // 4.1 查询领取数量
        String key = PromotionConstants.USER_COUPON_CACHE_KEY_PREFIX + couponId;
        Long count = redisTemplate.opsForHash().increment(key, UserContext.getUser().toString(), 1);
        // 4.2 校验限领数量
        if (count > coupon.getUserLimit()) {
            throw new BizIllegalException("超过领取数量");
        }
        // 5. 扣减库存
        redisTemplate.opsForHash().increment(PromotionConstants.COUPON_CACHE_KEY_PREFIX + couponId, "totalNum", -1);
        // 6. 发送MQ消息
        UserCouponDTO userCouponDTO = new UserCouponDTO();
        userCouponDTO.setUserId(UserContext.getUser());
        userCouponDTO.setCouponId(couponId);
        mqHelper.send(MqConstants.Exchange.PROMOTION_EXCHANGE, MqConstants.Key.COUPON_RECEIVE, userCouponDTO);
    }

    /**
     * 从缓存查询优惠券
     */
    private Coupon queryCouponByCache(Long couponId) {
        // 1. 准备key
        String key = PromotionConstants.COUPON_CACHE_KEY_PREFIX + couponId;
        // 2. 查询数据
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        if (CollUtils.isEmpty(entries)) {
            return null;
        }
        // 3. 数据反序列化
        return BeanUtils.mapToBean(entries, Coupon.class, false, CopyOptions.create());
    }

    @Transactional
    @Override
    public void checkAndCreateUserCoupon(UserCouponDTO ucd) {
        // 1. 查询优惠券
        Coupon coupon = couponMapper.selectById(ucd.getCouponId());
        if (coupon == null) {
            throw new BizIllegalException("优惠券不存在");
        }
        // 2. 更新优惠券发放数量+1
        int sucRow = couponMapper.incrIssueNum(coupon.getId());
        if (sucRow == 0) {
            throw new BizIllegalException("优惠券库存不足");
        }
        Long userId = ucd.getUserId();
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
    @Lock(name = "lock:coupon:#{T(com.tianji.common.utils.UserContext).getUser()}")
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
            // 3. 查询兑换码对应的优惠券id
            Long couponId = exchangeCodeService.exchangeTargetId(serialNum);
            if (Objects.isNull(couponId)) {
                throw new BizIllegalException("兑换码不存在");
            }
            Coupon coupon = queryCouponByCache(couponId);
            // 4. 校验发放时间
            LocalDateTime now = LocalDateTime.now();
            if (now.isBefore(coupon.getIssueBeginTime()) || now.isAfter(coupon.getIssueEndTime())) {
                throw new BadRequestException("优惠券发放结束或尚未开始");
            }
            // 5. 校验每人限领数量
            Long userId = UserContext.getUser();
            // 5.1 查询领取数量
            String key = PromotionConstants.USER_COUPON_CACHE_KEY_PREFIX + couponId;
            Long count = redisTemplate.opsForHash().increment(key, userId.toString(), 1);
            // 5.2 校验限领数量
            if (count > coupon.getUserLimit()) {
                throw new BizIllegalException("超过领取数量");
            }
            // 6. 发送MQ消息通知
            UserCouponDTO userCouponDTO = new UserCouponDTO();
            userCouponDTO.setUserId(userId);
            userCouponDTO.setCouponId(couponId);
            mqHelper.send(MqConstants.Exchange.PROMOTION_EXCHANGE, MqConstants.Key.COUPON_RECEIVE, userCouponDTO);
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

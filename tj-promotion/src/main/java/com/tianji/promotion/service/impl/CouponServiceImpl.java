package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.pojo.Coupon;
import com.tianji.promotion.domain.pojo.CouponScope;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponDetailVO;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponScopeVO;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ObtainType;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.ICouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.service.IExchangeCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.tianji.promotion.constants.PromotionConstants.COUPON_CODE_SERIAL_KEY;

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
    private final IExchangeCodeService exchangeCodeService;
    private final CategoryCache categoryCache;
    private final StringRedisTemplate redisTemplate;

    /**
     * 新增优惠券
     */
    @Override
    @Transactional
    public void saveCoupon(CouponFormDTO couponFormDTO) {
        // 1. 保存优惠券信息
        Coupon coupon = BeanUtils.copyBean(couponFormDTO, Coupon.class);
        save(coupon);
        // 2. 保存优惠券使用范围信息
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

    /**
     * 发放优惠券
     */
    @Override
    public void grantCoupon(CouponIssueFormDTO couponIssueFormDTO) {
        // 1. 查询优惠券
        Coupon coupon = getById(couponIssueFormDTO.getId());
        if (Objects.isNull(coupon)) {
            throw new BadRequestException("优惠券不存在");
        }
        // 2. 判断优惠券状态，判断状态是否为暂停或待发放
        if (!(coupon.getStatus() == CouponStatus.DRAFT) && !(coupon.getStatus() == CouponStatus.PAUSE)) {
            throw new BadRequestException("优惠券状态错误");
        }
        // 3. 判断是否为立刻发放
        // 如果开始发放时间为 null或者发放时间小于等于当前时间，则为立刻发放
        LocalDateTime now = LocalDateTime.now();
        boolean isInstantly = couponIssueFormDTO.getIssueBeginTime() == null
                            || !couponIssueFormDTO.getIssueBeginTime().isAfter(now);
        // 4. 更新优惠券
        // 4.1 拷贝属性
        Coupon c = BeanUtils.copyBean(couponIssueFormDTO, Coupon.class);
        // 4.2 更新发布时间以及发布状态
        if (isInstantly) {
            c.setIssueBeginTime(now);
            c.setStatus(CouponStatus.ISSUING);
        } else {
            c.setStatus(CouponStatus.UN_ISSUE);
        }
        // 4.3 写入数据库
        updateById(c);
        // 5. 判断是否需要生成兑换码
        if (coupon.getObtainWay() == ObtainType.ISSUE && coupon.getStatus() == CouponStatus.DRAFT) {
            coupon.setIssueBeginTime(c.getIssueBeginTime());
            exchangeCodeService.asyncGenerateExchangeCode(coupon);
        }
    }

    /**
     * 更新优惠券
     */
    @Override
    public void updateCoupon(CouponFormDTO couponFormDTO) {
        // 1. 查询优惠券
        Coupon coupon = getById(couponFormDTO.getId());
        if (Objects.isNull(coupon)) {
            throw new BadRequestException("优惠券不存在");
        }
        if (coupon.getStatus() != CouponStatus.DRAFT) {
            // 只有待发放的优惠券才可以修改
            throw new BadRequestException("只有待发放的优惠券才可以修改");
        }
        // 2. 更新优惠券信息
        Coupon c = BeanUtils.copyBean(couponFormDTO, Coupon.class);
        c.setId(coupon.getId());
        updateById(c);
        // 3. 更新优惠券使用范围信息
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
        couponScopeService.updateBatchById(couponScopeList);
    }

    /**
     * 删除优惠券
     */
    @Override
    public void deleteCouponById(Long id) {
        // 1. 查询优惠券
        Coupon coupon = getById(id);
        // 2. 判断优惠券
        if (Objects.isNull(coupon) || coupon.getStatus() != CouponStatus.DRAFT) {
            throw new BadRequestException("优惠券不存在或优惠券不处于待发放状态");
        }
        // 3. 删除优惠券
        removeById(id);
        // 4. 删除优惠券的使用范围
        if (!coupon.getSpecific()) {
            return;
        }
        couponScopeService.remove(new LambdaQueryWrapper<CouponScope>()
                .eq(CouponScope::getCouponId, id));
    }

    /**
     * 根据id查询指定优惠券信息
     */
    @Override
    public CouponDetailVO queryCouponDetailById(Long id) {
        // 1. 查询优惠券数据
        Coupon coupon = getById(id);
        if (Objects.isNull(coupon)) {
            throw new BadRequestException("优惠券不存在");
        }
        // 2. 封装优惠券数据
        CouponDetailVO couponDetailVO = BeanUtils.copyBean(coupon, CouponDetailVO.class);
        // 3. 封装优惠券范围数据
        if (!coupon.getSpecific()) {
            return couponDetailVO;
        }
        List<CouponScope> list = couponScopeService.lambdaQuery()
                .eq(CouponScope::getCouponId, id)
                .list();
        if (CollUtils.isEmpty(list)) {
            return couponDetailVO;
        }
        List<CouponScopeVO> couponScopeVOList = list.stream().map(CouponScope::getId) // 取出所有 id
                .map(couponScopeId -> new CouponScopeVO(couponScopeId, categoryCache.getNameByLv3Id(couponScopeId)))
                .collect(Collectors.toList());
        couponDetailVO.setScopes(couponScopeVOList);
        return couponDetailVO;
    }

    /**
     * 批量发布优惠券
     */
    @Override
    public void beginIssueCouponBatch(List<Coupon> records) {
        // 1. 批量更新优惠券发布状态为发布状态
        if (CollUtils.isEmpty(records)) {
            return;
        }
        for (Coupon record : records) {
            record.setStatus(CouponStatus.ISSUING);
        }
        updateBatchById(records);
        // 2. 批量缓存
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection src = (StringRedisConnection) connection;// 强转为 StringRedisConnection
            for (Coupon record : records) {
                Map<String, String> map = new HashMap<>(4);
                map.put("issueBeginTime", String.valueOf(DateUtils.toEpochMilli(record.getIssueBeginTime())));
                map.put("issueEndTime", String.valueOf(DateUtils.toEpochMilli(record.getIssueEndTime())));
                map.put("totalNum", String.valueOf(record.getTotalNum()));
                map.put("userLimit", String.valueOf(record.getUserLimit()));
                // 写入缓存
                // mSet用于同时设置多个键值对
                src.hMSet(COUPON_CODE_SERIAL_KEY + record.getId(), map);
            }
            return null;
        });
    }
}

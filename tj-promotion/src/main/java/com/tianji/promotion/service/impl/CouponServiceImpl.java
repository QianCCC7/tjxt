package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.*;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.pojo.Coupon;
import com.tianji.promotion.domain.pojo.CouponScope;
import com.tianji.promotion.domain.pojo.UserCoupon;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponDetailVO;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponScopeVO;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ObtainType;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.ICouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.tianji.promotion.constants.PromotionConstants.COUPON_CACHE_KEY_PREFIX;
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
@Slf4j
@RequiredArgsConstructor
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {
    private final ICouponScopeService couponScopeService;
    private final IExchangeCodeService exchangeCodeService;
    private final CategoryCache categoryCache;
    private final StringRedisTemplate redisTemplate;
    private final IUserCouponService userCouponService;

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
    @Transactional
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
        // 4.4 添加缓存
        if (isInstantly) {// 是立即发放的优惠券才写入缓存
            coupon.setIssueBeginTime(c.getIssueBeginTime());
            coupon.setIssueEndTime(c.getIssueEndTime());
            cacheCouponInfo(coupon);
        }
        // 5. 判断是否需要生成兑换码
        if (coupon.getObtainWay() == ObtainType.ISSUE && coupon.getStatus() == CouponStatus.DRAFT) {
            coupon.setIssueEndTime(c.getIssueEndTime());
            exchangeCodeService.asyncGenerateExchangeCode(coupon);
        }
    }

    /**
     * 将优惠券信息写入缓存
     */
    private void cacheCouponInfo(Coupon coupon) {
        // 1. 组装数据
        Map<String, String> map = new HashMap<>(4);
        map.put("issueBeginTime", String.valueOf(DateUtils.toEpochMilli(coupon.getIssueBeginTime())));
        map.put("issueEndTime", String.valueOf(DateUtils.toEpochMilli(coupon.getIssueEndTime())));
        map.put("totalNum", String.valueOf(coupon.getTotalNum()));
        map.put("userLimit", String.valueOf(coupon.getUserLimit()));

        // 2. 写入缓存
        redisTemplate.opsForHash().putAll(COUPON_CACHE_KEY_PREFIX + coupon.getId(), map);
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

    /**
     * 批量结束优惠券(删缓存)
     */
    @Override
    public void finishIssueCouponBatch(List<Coupon> records) {
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection src = (StringRedisConnection) connection;// 强转为 StringRedisConnection
            for (Coupon record : records) {
                src.unlink(COUPON_CODE_SERIAL_KEY + record.getId());
            }
            return null;
        });
    }

    /**
     * 暂停发布优惠券
     */
    @Override
    @Transactional
    public void pauseIssueCoupon(Long id) {
        // 1.查询优惠券
        Coupon coupon = getById(id);
        if (Objects.isNull(coupon)) {
            throw new BadRequestException("优惠券不存在");
        }
        // 即已发放的优惠券可以暂停发布(未开始或者发放中的可以暂停)
        if (coupon.getStatus() != CouponStatus.UN_ISSUE && coupon.getStatus() != CouponStatus.ISSUING) {
            return;
        }
        // 2.更新优惠券状态
        boolean success = lambdaUpdate()
                .eq(Coupon::getId, id)
                .set(Coupon::getStatus, CouponStatus.PAUSE)
                .in(Coupon::getStatus, CouponStatus.UN_ISSUE, CouponStatus.ISSUING)
                .update();
        if (!success) {
            log.error("重复暂停发布优惠券{}", id);
        }
        // 3.删除缓存
        redisTemplate.delete(COUPON_CACHE_KEY_PREFIX + id);
    }

    /**
     * 用户端分页查看发放中且手动领取的优惠券
     */
    @Override
    public List<CouponVO> queryIssuingCoupon() {
        // 1. 查询发放中且手动领取的优惠券
        List<Coupon> couponList = lambdaQuery()
                .eq(Coupon::getStatus, CouponStatus.ISSUING)
                .eq(Coupon::getObtainWay, ObtainType.PUBLIC)
                .list();
        if (CollUtils.isEmpty(couponList)) {
            return CollUtils.emptyList();
        }
        // 2. 统计当前用户已经领取的优惠券信息(用于前端动态展示优惠券状态)
        List<Long> couponIdList = couponList.stream()
                .map(Coupon::getId)
                .collect(Collectors.toList());
        // 2.1 查询当前用户已经领取的优惠券数据
        List<UserCoupon> userCouponList = userCouponService.lambdaQuery()
                .eq(UserCoupon::getUserId, UserContext.getUser())
                .in(UserCoupon::getCouponId, couponIdList)
                .list();
        // 2.2 统计当前用户对指定优惠券领取的数量
        Map<Long, Long> issuedMap = userCouponList.stream()
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));
        // 2.3 统计当前用户对优惠券已经领取并未使用的数量
        Map<Long, Long> unusedMap = userCouponList.stream()
                .filter(uc -> uc.getStatus() == UserCouponStatus.UNUSED)
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));
        // 3. 封装 vo数据
        List<CouponVO> couponVOList = new ArrayList<>(couponList.size());
        for (Coupon coupon : couponList) {
            // 3.1 拷贝基础属性
            CouponVO couponVO = BeanUtils.copyBean(coupon, CouponVO.class);
            // 3.2 是否可以领取:
            // 3.2.1 优惠券已被领取的数量 < 优惠券总的数量
            // 3.2.2 用户领取该优惠券的数量 < 每个用户限领的优惠券数量
            couponVO.setAvailable(coupon.getIssueNum() < coupon.getTotalNum()
                                && issuedMap.getOrDefault(coupon.getId(), 0L) < coupon.getUserLimit());
            // 3.3 是否可以使用:是否存在已经领取且未使用的优惠券
            couponVO.setReceived(unusedMap.getOrDefault(coupon.getId(), 0L) > 0);
            couponVOList.add(couponVO);
        }
        return couponVOList;
    }
}

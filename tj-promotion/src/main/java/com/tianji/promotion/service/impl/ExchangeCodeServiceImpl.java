package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.pojo.Coupon;
import com.tianji.promotion.domain.pojo.ExchangeCode;
import com.tianji.promotion.domain.query.CodeQuery;
import com.tianji.promotion.domain.vo.ExchangeCodeVO;
import com.tianji.promotion.mapper.ExchangeCodeMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.utils.CodeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.tianji.promotion.constants.PromotionConstants.COUPON_CODE_MAP_KEY;

/**
 * <p>
 * 兑换码 服务实现类
 * </p>
 *
 * @author QianCCC
 * @since 2024-02-07
 */
@Service
public class ExchangeCodeServiceImpl extends ServiceImpl<ExchangeCodeMapper, ExchangeCode> implements IExchangeCodeService {
    private BoundValueOperations<String, String> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    public ExchangeCodeServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate.boundValueOps(PromotionConstants.COUPON_CODE_SERIAL_KEY);
        this.stringRedisTemplate = redisTemplate;
    }

    /**
     * 异步生成兑换码
     */
    @Async("generateExchangeCodeExecutor")
    @Override
    public void asyncGenerateExchangeCode(Coupon coupon) {
        // 1. 获取优惠券发放数量
        Integer totalNum = coupon.getTotalNum();
        // 2. 获取Redis的自增序列号
        Long maxSerialNum = redisTemplate.increment(totalNum);
        if (maxSerialNum == null) return;
        List<ExchangeCode> list = new ArrayList<>(totalNum);
        for (long serialNum = maxSerialNum - totalNum + 1; serialNum <= maxSerialNum; serialNum++) {
            // 3. 生成兑换码
            String code = CodeUtil.generateCode(serialNum, coupon.getId());
            ExchangeCode exchangeCode = new ExchangeCode();
            exchangeCode.setId((int) serialNum);
            exchangeCode.setCode(code);
            exchangeCode.setExchangeTargetId(coupon.getId());
            exchangeCode.setExpiredTime(coupon.getIssueEndTime());
            list.add(exchangeCode);
        }
        // 4. 写入数据库
        saveBatch(list);
        // 5. 生成兑换码时，将优惠券及对应兑换码序列号的最大值缓存到Redis中(member:couponId,score:兑换码的最大序列号)
        stringRedisTemplate.opsForZSet()
                .add(PromotionConstants.COUPON_RANGE_KEY, coupon.getId().toString(), maxSerialNum);
    }

    /**
     * 分页查询兑换码
     */
    @Override
    public PageDTO<ExchangeCodeVO> queryExchangeCodePage(CodeQuery codeQuery) {
        Page<ExchangeCode> page = lambdaQuery()
                .eq(ExchangeCode::getExchangeTargetId, codeQuery.getCouponId())
                .eq(ExchangeCode::getStatus, codeQuery.getStatus())
                .page(codeQuery.toMpPage());
        List<ExchangeCode> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        List<ExchangeCodeVO> exchangeCodeVOList = BeanUtils.copyList(records, ExchangeCodeVO.class);
        return PageDTO.of(page, exchangeCodeVOList);
    }

    /**
     * 更新兑换码状态
     * @param serialNum 兑换码的序列号
     * @param mark 将要更新的状态：使用或者未使用
     */
    @Override
    public boolean updateExchangeCodeMark(long serialNum, boolean mark) {
        Boolean setMark = stringRedisTemplate.opsForValue().setBit(COUPON_CODE_MAP_KEY, serialNum, mark);
        return setMark != null && setMark;
    }

    /**
     * 查询指定序列号(兑换码)对应的优惠券 id
     */
    @Override
    public Long exchangeTargetId(long serialNum) {
        Set<String> set = stringRedisTemplate.opsForZSet()
                .rangeByScore(PromotionConstants.COUPON_RANGE_KEY, serialNum, serialNum + 5000,
                        0L, 1L);
        if (CollUtils.isEmpty(set)) {
            return null;
        }
        String next = set.iterator().next();
        return Long.parseLong(next);
    }
}

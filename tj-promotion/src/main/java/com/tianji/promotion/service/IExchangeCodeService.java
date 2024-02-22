package com.tianji.promotion.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.pojo.Coupon;
import com.tianji.promotion.domain.pojo.ExchangeCode;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.promotion.domain.query.CodeQuery;
import com.tianji.promotion.domain.vo.ExchangeCodeVO;

/**
 * <p>
 * 兑换码 服务类
 * </p>
 *
 * @author QianCCC
 * @since 2024-02-07
 */
public interface IExchangeCodeService extends IService<ExchangeCode> {

    void asyncGenerateExchangeCode(Coupon coupon);

    PageDTO<ExchangeCodeVO> queryExchangeCodePage(CodeQuery codeQuery);

    boolean updateExchangeCodeMark(long serialNum, boolean b);

    Long exchangeTargetId(long serialNum);
}

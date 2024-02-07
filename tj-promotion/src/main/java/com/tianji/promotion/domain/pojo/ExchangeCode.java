package com.tianji.promotion.domain.pojo;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;

import com.tianji.promotion.enums.ExchangeCodeStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 兑换码
 * </p>
 *
 * @author QianCCC
 * @since 2024-02-07
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("exchange_code")
public class ExchangeCode implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 兑换码id
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Integer id;

    /**
     * 兑换码
     */
    private String code;

    /**
     * 兑换码状态， 1：待兑换，2：已兑换，3：兑换活动已结束
     */
    private ExchangeCodeStatus status;

    /**
     * 兑换人
     */
    private Long userId;

    /**
     * 兑换类型，1：优惠券，以后再添加其它类型
     */
    private Integer type;

    /**
     * 兑换码目标id，例如兑换优惠券，该id则是优惠券的配置id
     */
    private Long exchangeTargetId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 兑换码过期时间
     */
    private LocalDateTime expiredTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;


}

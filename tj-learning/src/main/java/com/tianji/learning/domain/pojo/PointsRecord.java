package com.tianji.learning.domain.pojo;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;

import com.tianji.learning.enums.PointsRecordType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 学习积分记录，每个月底清零
 * </p>
 *
 * @author QianCCC
 * @since 2024-01-28
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("points_record")
public class PointsRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 积分记录表id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 积分方式：1-课程学习，2-每日签到，3-课程问答， 4-课程笔记，5-课程评价
     */
    private PointsRecordType type;

    /**
     * 积分值
     */
    private Integer points;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;


}

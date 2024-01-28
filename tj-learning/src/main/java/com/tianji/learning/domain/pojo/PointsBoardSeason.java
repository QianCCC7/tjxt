package com.tianji.learning.domain.pojo;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDate;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 赛季表
 * </p>
 *
 * @author QianCCC
 * @since 2024-01-28
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("points_board_season")
public class PointsBoardSeason implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 自增长id，season标示
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 赛季名称，例如：第1赛季
     */
    private String name;

    /**
     * 赛季开始时间
     */
    private LocalDate beginTime;

    /**
     * 赛季结束时间
     */
    private LocalDate endTime;


}

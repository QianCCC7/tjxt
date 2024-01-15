package com.tianji.learning.domain.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 学习记录表
 * </p>
 *
 * @author QianCCC
 * @since 2023-12-19
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("learning_record")
public class LearningRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 学习记录的id
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 对应课表的id
     */
    @TableField("lesson_id")
    private Long lessonId;

    /**
     * 对应小节的id
     */
    @TableField("section_id")
    private Long sectionId;

    /**
     * 用户id
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 视频的当前观看时间点，单位秒
     */
    @TableField("moment")
    private Integer moment;

    /**
     * 是否完成学习，默认false
     */
    @TableField("finished")
    private Boolean finished;

    /**
     * 第一次观看时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 完成学习的时间
     */
    @TableField("finish_time")
    private LocalDateTime finishTime;

    /**
     * 更新时间（最近一次观看时间）
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
}

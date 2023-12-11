package com.tianji.learning.domain.pojo;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import lombok.Data;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;

/**
 * 学生课程表
 *
 * @author QianCCC
 * @since 2023-12-06 23:59
 */
@Data
@TableName("learning_lesson")
public class LearningLesson implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID) // 雪花算法生成 id
    private Long id;

    /**
     * 学员id
     */
    private Long userId;

    /**
     * 课程id
     */
    private Long courseId;

    /**
     * 课程状态，0-未学习，1-学习中，2-已学完，3-已失效
     */
    private LessonStatus status;

    /**
     * 每周学习频率，例如每周学习6小节，则频率为6
     */
    private Integer weekFreq;

    /**
     * 学习计划状态，0-没有计划，1-计划进行中
     */
    private PlanStatus planStatus;

    /**
     * 已学习小节数量
     */
    private Integer learnedSections;

    /**
     * 最近一次学习的小节id
     */
    private Long latestSectionId;

    /**
     * 最近一次学习的时间
     */
    private LocalDateTime latestLearnTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}

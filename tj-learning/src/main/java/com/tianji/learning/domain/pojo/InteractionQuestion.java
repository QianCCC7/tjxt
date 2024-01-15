package com.tianji.learning.domain.pojo;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;

import com.tianji.learning.enums.QuestionStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 互动提问的问题表
 * </p>
 *
 * @author QianCCC
 * @since 2024-01-15
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("interaction_question")
public class InteractionQuestion implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键，互动问题的id
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 互动问题的标题
     */
    private String title;

    /**
     * 问题描述信息
     */
    private String description;

    /**
     * 所属课程id
     */
    private Long courseId;

    /**
     * 所属课程章id
     */
    private Long chapterId;

    /**
     * 所属课程节id
     */
    private Long sectionId;

    /**
     * 提问学员id
     */
    private Long userId;

    /**
     * 最新的一个回答的id
     */
    private Long latestAnswerId;

    /**
     * 问题下的回答数量
     */
    private Integer answerTimes;

    /**
     * 是否匿名，默认false
     */
    private Boolean anonymity;

    /**
     * 是否被隐藏，默认false
     */
    private Boolean hidden;

    /**
     * 管理端问题状态：0-未查看，1-已查看
     */
    private QuestionStatus status;

    /**
     * 提问时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;


}

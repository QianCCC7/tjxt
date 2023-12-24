package com.tianji.learning.mapper;

import com.tianji.api.dto.IdAndNumDTO;
import com.tianji.learning.domain.pojo.LearningRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 学习记录表 Mapper 接口
 * </p>
 *
 * @author QianCCC
 * @since 2023-12-19
 */
public interface LearningRecordMapper extends BaseMapper<LearningRecord> {

    List<IdAndNumDTO> learnedSectionsCount(
            @Param("userId") Long userId,
            @Param("beginTime") LocalDateTime beginTime,
            @Param("endTime") LocalDateTime endTime);
}

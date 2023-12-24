package com.tianji.learning.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.tianji.learning.domain.pojo.LearningLesson;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
* 学生课程表 Mapper
*
* @author QianCCC
* @since 2023-12-06 23:59
*/
@Mapper
public interface LearningLessonMapper extends BaseMapper<LearningLesson> {

    /**
     * 根据主键id查询
     *
     * @param id
     * @return 记录信息
     */
    LearningLesson selectByPrimaryKey(Long id);

    /**
     * 插入数据库记录（不建议使用）
     *
     * @param record
     */
    int insert(LearningLesson record);

    /**
     * 插入数据库记录（建议使用）
     *
     * @param record 插入数据
     * @return 插入数量
     */
    int insertSelective(LearningLesson record);

    /**
     * 修改数据(推荐使用)
     *
     * @param record 更新值
     * @return 更新数量
     */
    int updateByPrimaryKeySelective(LearningLesson record);

    /**
     * 根据主键更新数据
     *
     * @param record 更新值
     * @return 更新数量
     */
    int updateByPrimaryKey(LearningLesson record);

    /**
     * 查询用户本周学习计划总数
     * @param userId
     * @return
     */
    Integer queryTotalPlans(Long userId);
}

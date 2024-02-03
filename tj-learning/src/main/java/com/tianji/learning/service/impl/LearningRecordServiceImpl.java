package com.tianji.learning.service.impl;

import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.domain.pojo.LearningLesson;
import com.tianji.learning.domain.pojo.LearningRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.SectionType;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.mq.message.SignInMessage;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.ILearningRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.utils.LearningRecordDelayTaskHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * <p>
 * 学习记录表 服务实现类
 * </p>
 *
 * @author QianCCC
 * @since 2023-12-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {
    private final ILearningLessonService learningLessonService;
    private final CourseClient courseClient;
    private final LearningRecordDelayTaskHandler taskHandler;
    private final RabbitMqHelper mqHelper;

    /**
     * 查询指定课程的学习记录
     *
     * @param courseId
     * @return
     */
    @Override
    public LearningLessonDTO queryLearningRecordByCourse(Long courseId) {
        // 1. 获取登录用户
        Long userId = UserContext.getUser();
        // 2. 根据用户Id和课程Id查询课表信息，包含了学习状态，学习的小节数和最近学习的小节等信息
        LearningLesson lesson = learningLessonService.queryLessonByUserIdAndCourseId(userId, courseId);
        if (Objects.isNull(lesson)) {
            log.debug("查询不到用户{}对课程{}的学习信息", userId, courseId);
            return null;// 用户没有购买过课程，但是可以观看可以试看的视频
        }
        // 3. 查询用户对该课程的学习记录
        List<LearningRecord> learningRecords = lambdaQuery()
                .eq(LearningRecord::getLessonId, lesson.getId())
                .list();
        // 4. 封装结果DTO
        LearningLessonDTO learningLessonDTO = new LearningLessonDTO();
        learningLessonDTO.setId(lesson.getId());// 课表id
        learningLessonDTO.setLatestSectionId(lesson.getLatestSectionId());
        // 因为只需要知道学习记录的部分信息(即LearningLessonDTO)
        List<LearningRecordDTO> learningRecordDTOS = BeanUtils.copyList(learningRecords, LearningRecordDTO.class);
        learningLessonDTO.setRecords(learningRecordDTOS);
        return learningLessonDTO;
    }

    /**
     * 用户提交学习记录的接口
     */
    @Override
    @Transactional
    public void addLearningRecord(LearningRecordFormDTO formDTO) {
        // 1. 获取登录用户
        Long userId = UserContext.getUser();
        // 2. 处理学习记录
        boolean finished;// 该小节是否学完
        if (formDTO.getSectionType() == SectionType.VIDEO) {
            // 2.1 处理视频记录
            finished = handleVideoRecord(userId, formDTO);
        } else {
            // 2.2 处理考试记录
            finished = handleExamRecord(userId, formDTO);
        }
        if (!finished) {
            // 没有新完成的小结，直接 return，接下来的交给延迟任务处理
            return;
        } else {
            mqHelper.send(MqConstants.Exchange.LEARNING_EXCHANGE,
                    MqConstants.Key.LEARN_SECTION,
                    SignInMessage.of(userId, 10));
        }
        // 3. 处理课表数据
        handleLearningLessonChange(formDTO);
    }

    // 处理视频记录，返回值为是否学完改小节
    private boolean handleVideoRecord(Long userId, LearningRecordFormDTO formDTO) {
        // 1. 通过lessonId和sectionId查询旧的学习记录(即是否存在该学习记录)
        LearningRecord old = queryOldRecord(formDTO.getLessonId(), formDTO.getSectionId());
        // 2. 判断有无旧的学习记录
        if (Objects.isNull(old)) {
            // 3. 不存在旧的学习记录，则新增学习记录
            // 3.1 拷贝数据
            LearningRecord record = BeanUtils.copyBean(formDTO, LearningRecord.class);
            record.setUserId(userId);
            record.setFinished(false);// 该小节并未学完，因为是刚点开的视频，不可能刚点开视频后的第一次提交就学完该小节(5秒提交一次)，所以是 false
            record.setCreateTime(formDTO.getCommitTime());
            record.setFinishTime(formDTO.getCommitTime());
            record.setUpdateTime(formDTO.getCommitTime());
            boolean suc = save(record);
            if (!suc) {
                throw new DbException("新增学习记录失败！");
            }
            return false;// 因为是刚点开的视频，不可能刚点开视频后的第一次提交就学完该小节(5秒提交一次)，所以返回 false
        }
        // 4. 存在旧的学习记录，则修改学习记录
        // 4.1 判断是否为第一次学完
        boolean firstFinished = !old.getFinished() && formDTO.getMoment() * 2 >= formDTO.getDuration();
        if (!firstFinished) {
            // 不是第一次学完，将数据写入缓存，更新最近学习的小结和时间
            LearningRecord record = new LearningRecord();
            record.setLessonId(formDTO.getLessonId());
            record.setSectionId(formDTO.getSectionId());
            record.setMoment(formDTO.getMoment());
            record.setId(old.getId());
            record.setFinished(old.getFinished());
            taskHandler.addLearningRecordTask(record);
            return false;// 不是第一次学完
        }
        boolean suc = lambdaUpdate()
                .set(LearningRecord::getMoment, formDTO.getMoment())
                .set(LearningRecord::getFinished, true)
                .set(LearningRecord::getFinishTime, formDTO.getCommitTime())
                .eq(LearningRecord::getId, old.getId())
                .update();
        if (!suc) {
            throw new DbException("新增学习记录失败！");
        }
        // 清理缓存
        taskHandler.clearRecordCache(formDTO.getLessonId(), formDTO.getSectionId());
        return true;
    }

    // 查询是否存在指定的学习记录
    private LearningRecord queryOldRecord(Long lessonId, Long sectionId) {
        // 1. 有限查询缓存
        LearningRecord record = taskHandler.readRecordCache(lessonId, sectionId);
        if (!Objects.isNull(record)) {// 缓存命中，直接返回缓存中查询到的数据
            return record;
        }
        // 2. 缓存中没有数据，再查询数据库
        record = lambdaQuery()
                    .eq(LearningRecord::getLessonId, lessonId)
                    .eq(LearningRecord::getSectionId, sectionId)
                    .one();
        // 3. 写入缓存
        taskHandler.writeRecordToCache(record);
        return record;
    }

    // 处理考试记录，返回值为是否学完改小节
    private boolean handleExamRecord(Long userId, LearningRecordFormDTO formDTO) {
        // 1. 转换DTO为PO
        LearningRecord record = BeanUtils.copyBean(formDTO, LearningRecord.class);
        // 2. 填充数据
        record.setUserId(userId);
        record.setFinished(true); // 当考试提交后，该小节默认学完
        record.setCreateTime(formDTO.getCommitTime());
        record.setFinishTime(formDTO.getCommitTime());
        record.setUpdateTime(formDTO.getCommitTime());
        // 3. 数据库写入数据
        boolean suc = save(record);
        if (!suc) {
            throw new DbException("新增考试记录失败！");
        }
        return true;
    }

    // 处理课表数据
    private void handleLearningLessonChange(LearningRecordFormDTO formDTO) {
        // 1. 查询课表
        LearningLesson lesson = learningLessonService.getById(formDTO.getLessonId());
        if (Objects.isNull(lesson)) {
            throw new BizIllegalException("课表不存在，无法更新数据");
        }
        // 2. 判断是否有新的小节
        boolean finishedAllLesson = false;
        // 3. 查询课程总章节数
        CourseFullInfoDTO courseInfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if (Objects.isNull(courseInfo)) {
            throw new BizIllegalException("课表不存在，无法更新数据");
        }
        // 4. 比较课程是否全部学完
        if (lesson.getLearnedSections() + 1 >= courseInfo.getSectionNum()) {
            finishedAllLesson = true;
        }
        // 5. 更新课表数据
        learningLessonService.lambdaUpdate()
                .set(finishedAllLesson, LearningLesson::getStatus, LessonStatus.FINISHED.getValue())// 课程学完
                .setSql("learned_sections = learned_sections + 1")// 章节学完
                .set(lesson.getLearnedSections() == 0, LearningLesson::getStatus, LessonStatus.LEARNING.getValue())// 第一次开始学
                .eq(LearningLesson::getId, lesson.getId())
                .update();
    }
}

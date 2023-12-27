package com.tianji.learning.utils;

import com.tianji.common.utils.JsonUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.learning.domain.pojo.LearningLesson;
import com.tianji.learning.domain.pojo.LearningRecord;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.DelayQueue;

@Slf4j
@Component
@RequiredArgsConstructor
public class LearningRecordDelayTaskHandler {

    @Data
    @NoArgsConstructor
    private static class RecordCacheData {
        private Long id;
        private Integer moment;
        private Boolean finished;

        public RecordCacheData(LearningRecord record) {
            this.id = record.getId();
            this.moment = record.getMoment();
            this.finished = record.getFinished();
        }
    }

    @Data
    @NoArgsConstructor
    private static class RecordTaskData {
        private Long lessonId;
        private Long sectionId;
        private Integer moment;

        public RecordTaskData(LearningRecord record) {
            this.lessonId = record.getLessonId();
            this.sectionId = record.getSectionId();
            this.moment = record.getMoment();
        }
    }

    private final StringRedisTemplate redisTemplate;
    private final LearningRecordMapper recordMapper;
    private final ILearningLessonService learningLessonService;

    private final DelayQueue<DelayTask<RecordTaskData>> queue = new DelayQueue<>();
    private final static String RECORD_KEY_TEMPLATE = "learning:record:{}";
    private static volatile boolean begin = false;

    // 当类的所有 Bean被初始化完成后就会被调用
    @PostConstruct
    public void init() {
        // 异步任务
        CompletableFuture.runAsync(this::handleDelayTask);
    }

    // 在整个容器销毁前，会调用该注解下的方法。
    @PreDestroy
    public void destroy() {
        begin = false;
        log.debug("延迟任务停止执行");
    }

    /**
     * 处理延迟任务
     */
    public void handleDelayTask() {
        while (begin) {
            try {
                // 1. 获取到期的延迟任务
                DelayTask<RecordTaskData> task = queue.take();
                // 2. 查询 Redis缓存
                RecordTaskData data = task.getData();
                LearningRecord record = readRecordCache(data.getLessonId(), data.getSectionId());
                if (Objects.isNull(record)) {
                    continue;
                }
                // 3. 比较缓存与延迟任务中的 moment是否相等
                if (!data.getMoment().equals(record.getMoment())) {
                    // 不一致，说明用户还在持续提交播放进度，放弃旧数据
                    continue;
                }
                // 4. 一致，说明播放停止，将播放进度数据持久化到数据库
                // 4.1 更新学习记录的 moment
                record.setFinished(null);// 由于缓存中的 finished可能不如数据库的新，所以不更新
                recordMapper.updateById(record);
                // 4.2 更新课表最新学习信息
                LearningLesson lesson = new LearningLesson();
                lesson.setId(data.getLessonId());
                lesson.setLatestSectionId(data.getSectionId());
                lesson.setLatestLearnTime(LocalDateTime.now());// 虽然是20秒之前的时间，但是用现在时间影响不大
                learningLessonService.updateById(lesson);
            } catch (InterruptedException e) {
                log.error("处理延迟任务异常！", e);
            }
        }
    }

    /**
     * 添加播放记录到缓存并添加延迟任务至延迟队列
     * @param record
     */
    public void addLearningRecordTask(LearningRecord record) {
        // 1. 添加数据到缓存
        writeRecordToCache(record);
        // 2. 提交延迟任务到延迟队列
        queue.add(new DelayTask<>(new RecordTaskData(record), Duration.ofSeconds(20)));
    }

    // 添加数据到缓存
    public void writeRecordToCache(LearningRecord record) {
        log.debug("更新学习记录的缓存数据");
        try {
            // 1. 数据转换
            String data = JsonUtils.toJsonStr(new RecordCacheData(record));
            // 2. 写入 Redis
            String key = StringUtils.format(RECORD_KEY_TEMPLATE, record.getLessonId());
            redisTemplate.opsForHash().put(key, record.getSectionId().toString(), data);
            // 3. 设置过期时间
            redisTemplate.expire(key, Duration.ofMinutes(1));
        } catch (Exception e) {
            log.error("添加学习记录的缓存数据缓存失败", e);
        }
    }

    /**
     * 在缓存中读取播放记录
     * @param lessonId
     * @param sectionId
     * @return
     */
    public LearningRecord readRecordCache(Long lessonId, Long sectionId) {
        try {
            // 1. 读取数据
            String key = StringUtils.format(RECORD_KEY_TEMPLATE, lessonId);
            Object cacheData = redisTemplate.opsForHash().get(key, sectionId.toString());
            if (Objects.isNull(cacheData)) {
                return null;
            }
            // 2. JSON数据转换
            return JsonUtils.toBean(cacheData.toString(), LearningRecord.class);
        } catch (Exception e) {
            log.error("缓存数据读取异常", e);
            return null;
        }
    }

    /**
     * 在缓存中删除播放记录
     * @param lessonId
     * @param sectionId
     */
    public void clearRecordCache(Long lessonId, Long sectionId) {
        String key = StringUtils.format(RECORD_KEY_TEMPLATE, lessonId);
        redisTemplate.opsForHash().delete(key, sectionId.toString());
    }
}

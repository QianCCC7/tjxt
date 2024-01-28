package com.tianji.learning.service.impl;

import com.tianji.learning.domain.pojo.PointsRecord;
import com.tianji.learning.mapper.PointsRecordMapper;
import com.tianji.learning.service.IPointsRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务实现类
 * </p>
 *
 * @author QianCCC
 * @since 2024-01-28
 */
@Service
public class PointsRecordServiceImpl extends ServiceImpl<PointsRecordMapper, PointsRecord> implements IPointsRecordService {

}

package com.tianji.remark.service;

import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.pojo.LikedRecord;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 点赞记录表 服务类
 * </p>
 *
 * @author QianCCC
 * @since 2024-01-23
 */
public interface ILikedRecordService extends IService<LikedRecord> {

    void addLikeRecord(LikeRecordFormDTO likeRecordFormDTO);
}

package com.tianji.api.remark;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class LikeTimesDTO {
    private Long bizId;
    private Integer likeTimes;
}

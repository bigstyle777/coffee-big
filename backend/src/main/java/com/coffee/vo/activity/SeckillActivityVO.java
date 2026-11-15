package com.coffee.vo.activity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 顾客端秒杀活动
 */
public record SeckillActivityVO(
        Long activityId,
        String title,
        LocalDateTime startTime,
        LocalDateTime endTime,
        List<SeckillProductVO> products
) {
}

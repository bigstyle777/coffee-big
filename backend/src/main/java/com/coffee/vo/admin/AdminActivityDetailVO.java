package com.coffee.vo.admin;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台活动详情
 */
public record AdminActivityDetailVO(
        Long id,
        String title,
        String type,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer isOpen,
        String status,
        Long fullAmount,
        Long reduceAmount,
        String remark,
        boolean allPreheated,
        List<AdminActivityProductVO> products
) {
}

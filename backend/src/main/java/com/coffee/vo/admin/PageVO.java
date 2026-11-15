package com.coffee.vo.admin;

import java.util.List;

/**
 * 通用分页结果
 */
public record PageVO<T>(long total, long pageNum, long pageSize, List<T> records) {

    public static <T> PageVO<T> empty(long pageNum, long pageSize) {
        return new PageVO<>(0, pageNum, pageSize, List.of());
    }
}

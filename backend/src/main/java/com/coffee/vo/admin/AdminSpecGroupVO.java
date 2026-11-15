package com.coffee.vo.admin;

import java.util.List;

/**
 * 规格组及其选项，商品编辑时用于勾选绑定
 */
public record AdminSpecGroupVO(
        Long id,
        String name,
        boolean required,
        boolean multiple,
        List<AdminSpecOptionVO> options
) {
}

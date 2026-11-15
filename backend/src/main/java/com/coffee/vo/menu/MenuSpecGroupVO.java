package com.coffee.vo.menu;

import java.util.List;

/**
 * 规格组
 *
 * @param required 是否必选
 * @param multiple 是否多选
 */
public record MenuSpecGroupVO(
        Long id,
        String name,
        Boolean required,
        Boolean multiple,
        List<MenuSpecOptionVO> options
) {
}

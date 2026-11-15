package com.coffee.vo.menu;

import java.util.List;

/**
 * 菜单商品
 *
 * @param price      基础价格（分），不含规格加价
 * @param stock      当前库存（杯）
 * @param specGroups 该商品可选的规格组
 */
public record MenuProductVO(
        Long id,
        String name,
        String picUrl,
        Long price,
        Integer stock,
        List<MenuSpecGroupVO> specGroups
) {
}

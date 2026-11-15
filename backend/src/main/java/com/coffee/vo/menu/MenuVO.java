package com.coffee.vo.menu;

import java.util.List;

/**
 * 菜单树：分类 -> 商品 -> 规格组 -> 规格选项
 */
public record MenuVO(List<MenuCategoryVO> categories) {
}

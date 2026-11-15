package com.coffee.vo.menu;

import java.util.List;

/**
 * 菜单分类
 */
public record MenuCategoryVO(Long id, String name, List<MenuProductVO> products) {
}

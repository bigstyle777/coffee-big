package com.coffee.service;

import com.coffee.vo.menu.MenuVO;

/**
 * 菜单业务
 */
public interface MenuService {

    /**
     * 一次性查询所有上架分类、商品与规格树
     */
    MenuVO listMenu();
}

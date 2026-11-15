package com.coffee.controller;

import com.coffee.common.Result;
import com.coffee.service.MenuService;
import com.coffee.vo.menu.MenuVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 菜单接口
 */
@RestController
@RequestMapping("/api/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    /**
     * 一次性返回完整菜单树，前端可直接渲染
     */
    @GetMapping("/list")
    public Result<MenuVO> listMenu() {
        return Result.ok(menuService.listMenu());
    }
}

package com.coffee.controller;

import com.coffee.common.Result;
import com.coffee.service.AdminDashboardService;
import com.coffee.vo.admin.DashboardSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 店长首页看板接口
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    /**
     * 今日订单、营收、出餐情况、热销 TOP3 与最近订单
     */
    @GetMapping("/summary")
    public Result<DashboardSummaryVO> summary() {
        return Result.ok(adminDashboardService.getTodaySummary());
    }
}

package com.coffee.service;

import com.coffee.vo.admin.DashboardSummaryVO;

/**
 * 店长首页看板
 */
public interface AdminDashboardService {

    /**
     * 今日订单、营收、出餐情况与热销饮品
     */
    DashboardSummaryVO getTodaySummary();
}

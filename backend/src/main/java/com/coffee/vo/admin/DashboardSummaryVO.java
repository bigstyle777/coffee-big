package com.coffee.vo.admin;

import java.util.List;

/**
 * 首页看板今日数据
 *
 * @param todayOrderCount   今日订单总数
 * @param todayRevenue      今日营收（分），仅统计已支付订单
 * @param todayReadyCount   今日已出餐订单数
 * @param todayPendingCount 当前待制作订单数
 * @param hotProducts       热销饮品 TOP3
 * @param recentOrders      最近 5 条订单
 */
public record DashboardSummaryVO(
        long todayOrderCount,
        long todayRevenue,
        long todayReadyCount,
        long todayPendingCount,
        List<HotProductVO> hotProducts,
        List<AdminOrderVO> recentOrders
) {
}

package com.coffee.service.impl;

import com.coffee.mapper.CoffeeOrderItemMapper;
import com.coffee.mapper.CoffeeOrderMapper;
import com.coffee.service.AdminDashboardService;
import com.coffee.service.AdminOrderService;
import com.coffee.vo.admin.DashboardSummaryVO;
import com.coffee.vo.admin.HotProductVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 店长首页看板实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final int RECENT_ORDER_LIMIT = 5;

    private final CoffeeOrderMapper orderMapper;
    private final CoffeeOrderItemMapper orderItemMapper;
    private final AdminOrderService adminOrderService;

    @Override
    public DashboardSummaryVO getTodaySummary() {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);

        long todayOrderCount = orderMapper.countCreatedBetween(start, end);
        Long revenue = orderMapper.sumPaidAmountBetween(start, end);
        long todayReadyCount = orderMapper.countReadyBetween(start, end);
        long todayPendingCount = orderMapper.countMakingBetween(start, end);

        List<HotProductVO> hotProducts = orderItemMapper.selectHotProducts(start, end)
                .stream()
                .map(row -> new HotProductVO(row.getProductName(), row.getTotalQuantity()))
                .toList();

        return new DashboardSummaryVO(
                todayOrderCount,
                revenue == null ? 0L : revenue,
                todayReadyCount,
                todayPendingCount,
                hotProducts,
                adminOrderService.listRecentOrders(RECENT_ORDER_LIMIT));
    }
}

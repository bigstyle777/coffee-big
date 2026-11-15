package com.coffee.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.coffee.common.BusinessException;
import com.coffee.dto.admin.UpdateOrderStatusRequest;
import com.coffee.entity.CoffeeActivity;
import com.coffee.entity.CoffeeOrder;
import com.coffee.entity.CoffeeOrderItem;
import com.coffee.entity.SysUser;
import com.coffee.enums.OrderStatus;
import com.coffee.enums.RefundStatus;
import com.coffee.enums.UserRole;
import com.coffee.event.OrderRefundRequestedEvent;
import com.coffee.event.OrderStatusChangedEvent;
import com.coffee.mapper.CoffeeActivityMapper;
import com.coffee.mapper.CoffeeOrderItemMapper;
import com.coffee.mapper.CoffeeOrderMapper;
import com.coffee.mapper.SysUserMapper;
import com.coffee.service.AdminOrderService;
import com.coffee.service.OrderService;
import com.coffee.vo.admin.AdminOrderItemVO;
import com.coffee.vo.admin.AdminOrderVO;
import com.coffee.vo.admin.PageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 店长订单后台实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminOrderServiceImpl implements AdminOrderService {

    private static final long MAX_PAGE_SIZE = 100L;
    private static final int MAX_RECENT_LIMIT = 20;
    private static final int MAX_CANCEL_REASON_LENGTH = 200;

    private final CoffeeOrderMapper orderMapper;
    private final CoffeeOrderItemMapper orderItemMapper;
    private final SysUserMapper userMapper;
    private final CoffeeActivityMapper activityMapper;
    private final OrderService orderService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public PageVO<AdminOrderVO> pageOrders(long pageNum, long pageSize, String status,
                                           LocalDateTime startTime, LocalDateTime endTime,
                                           String phone, String keyword) {
        long safePageNum = Math.max(pageNum, 1);
        long safePageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        if (StrUtil.isNotBlank(status) && !OrderStatus.isValid(status)) {
            throw new BusinessException("订单状态不合法");
        }
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw new BusinessException("开始时间不能晚于结束时间");
        }

        LambdaQueryWrapper<CoffeeOrder> wrapper = Wrappers.<CoffeeOrder>lambdaQuery()
                .eq(StrUtil.isNotBlank(status), CoffeeOrder::getStatus, status)
                .ge(startTime != null, CoffeeOrder::getCreateTime, startTime)
                .le(endTime != null, CoffeeOrder::getCreateTime, endTime);

        if (StrUtil.isNotBlank(phone)) {
            List<SysUser> users = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                    .like(SysUser::getPhone, phone.trim()));
            if (users.isEmpty()) {
                return PageVO.empty(safePageNum, safePageSize);
            }
            wrapper.in(CoffeeOrder::getUserId,
                    users.stream().map(SysUser::getId).toList());
        }
        if (StrUtil.isNotBlank(keyword)) {
            String trimmedKeyword = keyword.trim();
            wrapper.and(nested -> nested
                    .like(CoffeeOrder::getOrderNo, trimmedKeyword)
                    .or()
                    .like(CoffeeOrder::getPickupCode, trimmedKeyword));
        }

        long total = orderMapper.selectCount(wrapper);
        if (total == 0) {
            return PageVO.empty(safePageNum, safePageSize);
        }
        long offset = (safePageNum - 1) * safePageSize;
        List<CoffeeOrder> orders = orderMapper.selectList(wrapper
                .orderByDesc(CoffeeOrder::getCreateTime)
                .orderByDesc(CoffeeOrder::getId)
                .last("LIMIT " + safePageSize + " OFFSET " + offset));
        return new PageVO<>(total, safePageNum, safePageSize, buildOrderVOs(orders));
    }

    @Override
    public AdminOrderVO getOrderDetail(Long orderId) {
        if (orderId == null) {
            throw new BusinessException("订单 ID 不能为空");
        }
        CoffeeOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        return buildOrderVOs(List.of(order)).get(0);
    }

    @Override
    public List<AdminOrderVO> listRecentOrders(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_RECENT_LIMIT);
        List<CoffeeOrder> orders = orderMapper.selectList(Wrappers.<CoffeeOrder>lambdaQuery()
                .orderByDesc(CoffeeOrder::getCreateTime)
                .orderByDesc(CoffeeOrder::getId)
                .last("LIMIT " + safeLimit));
        return buildOrderVOs(orders);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOrderStatus(Long adminId, UpdateOrderStatusRequest request) {
        if (request == null || request.orderId() == null) {
            throw new BusinessException("订单 ID 不能为空");
        }
        String targetStatus = request.status();
        if (!OrderStatus.READY.name().equals(targetStatus) && !OrderStatus.COMPLETED.name().equals(targetStatus)) {
            throw new BusinessException("仅支持将订单更新为 READY 或 COMPLETED");
        }

        CoffeeOrder order = orderMapper.selectById(request.orderId());
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        String currentStatus = order.getStatus();
        if (OrderStatus.READY.name().equals(targetStatus) && !OrderStatus.MAKING.name().equals(currentStatus)) {
            throw new BusinessException("只有制作中的订单可以标记为待取餐");
        }
        if (OrderStatus.COMPLETED.name().equals(targetStatus) && !OrderStatus.READY.name().equals(currentStatus)) {
            throw new BusinessException("只有待取餐的订单可以标记为已完成");
        }

        // 带上原状态做条件更新，避免多名店员同时操作导致跳状态
        CoffeeOrder updateOrder = new CoffeeOrder();
        updateOrder.setStatus(targetStatus);
        if (OrderStatus.COMPLETED.name().equals(targetStatus)) {
            updateOrder.setFinishTime(LocalDateTime.now());
        }
        int updatedRows = orderMapper.update(updateOrder, Wrappers.<CoffeeOrder>lambdaUpdate()
                .eq(CoffeeOrder::getId, request.orderId())
                .eq(CoffeeOrder::getStatus, currentStatus));
        if (updatedRows == 0) {
            throw new BusinessException("订单状态已变化，请刷新后重试");
        }

        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                request.orderId(), currentStatus, targetStatus,
                adminId, UserRole.ADMIN.name(), "店长更新订单状态"));
        log.info("订单状态更新成功 orderId={}, {} -> {}, adminId={}",
                request.orderId(), currentStatus, targetStatus, adminId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long adminId, Long orderId, String reason) {
        if (orderId == null) {
            throw new BusinessException("订单 ID 不能为空");
        }
        if (reason != null && reason.trim().length() > MAX_CANCEL_REASON_LENGTH) {
            throw new BusinessException("取消原因不能超过 " + MAX_CANCEL_REASON_LENGTH + " 个字符");
        }

        CoffeeOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        String currentStatus = order.getStatus();
        // 幂等：已取消的订单直接返回
        if (OrderStatus.CANCELLED.name().equals(currentStatus)) {
            return;
        }
        if (!OrderStatus.PENDING_PAY.name().equals(currentStatus)
                && !OrderStatus.MAKING.name().equals(currentStatus)
                && !OrderStatus.READY.name().equals(currentStatus)) {
            throw new BusinessException("已完成订单不能取消");
        }

        CoffeeOrder updateOrder = new CoffeeOrder();
        updateOrder.setStatus(OrderStatus.CANCELLED.name());
        updateOrder.setCancelTime(LocalDateTime.now());
        boolean paid = OrderStatus.MAKING.name().equals(currentStatus)
                || OrderStatus.READY.name().equals(currentStatus);
        if (paid) {
            updateOrder.setRefundStatus(RefundStatus.REFUNDING.name());
            updateOrder.setRefundAmount(order.getPayAmount());
        }
        int updatedRows = orderMapper.update(updateOrder, Wrappers.<CoffeeOrder>lambdaUpdate()
                .eq(CoffeeOrder::getId, orderId)
                .eq(CoffeeOrder::getStatus, currentStatus));
        if (updatedRows == 0) {
            throw new BusinessException("订单状态已变化，请刷新后重试");
        }

        // 回滚库存与优惠券；已支付订单的退款由后续支付渠道处理
        orderService.restoreResourcesForCancelledOrder(orderId);

        String logRemark = buildCancelRemark(currentStatus, reason);
        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                orderId, currentStatus, OrderStatus.CANCELLED.name(),
                adminId, UserRole.ADMIN.name(), logRemark));
        if (paid) {
            // 事务提交后再调用支付渠道退款，避免外部请求拖长数据库事务
            eventPublisher.publishEvent(new OrderRefundRequestedEvent(orderId, reason));
        }
        log.info("店长取消订单成功 orderId={}, {} -> CANCELLED, adminId={}, reason={}",
                orderId, currentStatus, adminId, reason);
    }

    private String buildCancelRemark(String currentStatus, String reason) {
        String remark = StrUtil.isBlank(reason)
                ? "店长取消订单"
                : "店长取消订单：" + reason.trim();
        if (OrderStatus.MAKING.name().equals(currentStatus)
                || OrderStatus.READY.name().equals(currentStatus)) {
            remark = remark + "（已支付，退款待处理）";
        }
        return remark;
    }

    /**
     * 批量组装订单视图，避免逐单查询造成 N+1
     */
    private List<AdminOrderVO> buildOrderVOs(List<CoffeeOrder> orders) {
        if (orders.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> orderIds = orders.stream().map(CoffeeOrder::getId).toList();
        Map<Long, List<CoffeeOrderItem>> itemsByOrder = orderItemMapper.selectList(
                        Wrappers.<CoffeeOrderItem>lambdaQuery()
                                .in(CoffeeOrderItem::getOrderId, orderIds)
                                .orderByAsc(CoffeeOrderItem::getId))
                .stream()
                .collect(Collectors.groupingBy(CoffeeOrderItem::getOrderId));

        Set<Long> userIds = orders.stream().map(CoffeeOrder::getUserId).collect(Collectors.toSet());
        Map<Long, SysUser> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectByIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));

        Map<Long, CoffeeActivity> activityMap = loadActivityMap(orders);
        return orders.stream()
                .map(order -> toAdminOrderVO(
                        order,
                        userMap.get(order.getUserId()),
                        order.getActivityId() == null ? null : activityMap.get(order.getActivityId()),
                        itemsByOrder.getOrDefault(order.getId(), Collections.emptyList())))
                .toList();
    }

    private Map<Long, CoffeeActivity> loadActivityMap(List<CoffeeOrder> orders) {
        Set<Long> activityIds = orders.stream()
                .map(CoffeeOrder::getActivityId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (activityIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return activityMapper.selectByIds(activityIds).stream()
                .collect(Collectors.toMap(CoffeeActivity::getId, Function.identity()));
    }

    private AdminOrderVO toAdminOrderVO(CoffeeOrder order, SysUser user,
                                        CoffeeActivity activity, List<CoffeeOrderItem> items) {
        List<AdminOrderItemVO> itemVOs = items.stream()
                .map(item -> new AdminOrderItemVO(
                        item.getProductId(),
                        item.getProductName(),
                        item.getSpecText(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getSubtotalAmount()))
                .toList();
        return new AdminOrderVO(
                order.getId(),
                order.getOrderNo(),
                order.getOrderType() == null ? "NORMAL" : order.getOrderType(),
                order.getPickupCode(),
                order.getPickupType(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getDiscountAmount(),
                order.getActivityDiscountAmount() == null ? 0L : order.getActivityDiscountAmount(),
                order.getPayAmount(),
                order.getItemCount(),
                order.getRemark(),
                user == null ? "" : user.getNickname(),
                user == null ? "" : StrUtil.nullToEmpty(user.getPhone()),
                order.getPayChannel(),
                order.getRefundStatus(),
                order.getRefundAmount() == null ? 0L : order.getRefundAmount(),
                order.getRefundTime(),
                order.getActivityId(),
                activity == null ? null : activity.getTitle(),
                order.getCreateTime(),
                order.getPayTime(),
                order.getCancelTime(),
                itemVOs);
    }
}

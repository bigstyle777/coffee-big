package com.coffee.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.coffee.common.BusinessException;
import com.coffee.common.Result;
import com.coffee.dto.order.CreateOrderItemRequest;
import com.coffee.dto.order.CreateOrderRequest;
import com.coffee.dto.order.OrderItemSpecSnapshot;
import com.coffee.entity.CoffeeActivity;
import com.coffee.entity.CoffeeOrder;
import com.coffee.entity.CoffeeOrderItem;
import com.coffee.entity.CoffeeProduct;
import com.coffee.entity.CoffeeProductSpecGroup;
import com.coffee.entity.CoffeeSpecGroup;
import com.coffee.entity.CoffeeSpecOption;
import com.coffee.enums.OrderStatus;
import com.coffee.enums.PickupType;
import com.coffee.enums.UserRole;
import com.coffee.event.OrderStatusChangedEvent;
import com.coffee.mapper.CoffeeActivityMapper;
import com.coffee.mapper.CoffeeOrderItemMapper;
import com.coffee.mapper.CoffeeOrderMapper;
import com.coffee.mapper.CoffeeProductMapper;
import com.coffee.mapper.CoffeeProductSpecGroupMapper;
import com.coffee.mapper.CoffeeSpecGroupMapper;
import com.coffee.mapper.CoffeeSpecOptionMapper;
import com.coffee.service.ActivityService;
import com.coffee.service.CouponService;
import com.coffee.service.OrderService;
import com.coffee.service.SeckillResourceService;
import com.coffee.util.PickupCodeGenerator;
import com.coffee.vo.order.OrderCreateVO;
import com.coffee.vo.order.OrderItemVO;
import com.coffee.vo.order.OrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 订单业务实现
 * 核心下单流程全部在同一个本地事务中完成，不依赖任何消息队列
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    /** 商品上架状态 */
    private static final int PRODUCT_ON_SHELF = 1;
    /** 规格选项启用状态 */
    private static final int SPEC_OPTION_ENABLED = 1;
    /** 规格组必选 */
    private static final int SPEC_GROUP_REQUIRED = 1;
    /** 规格组多选 */
    private static final int SPEC_GROUP_MULTIPLE = 1;
    /** 单件商品数量下限 */
    private static final int MIN_QUANTITY = 1;
    /** 单件商品数量上限 */
    private static final int MAX_QUANTITY = 99;
    /** 单笔订单商品种类上限 */
    private static final int MAX_ITEM_TYPES = 20;
    /** 备注最大长度 */
    private static final int MAX_REMARK_LENGTH = 200;
    /** 客户端幂等键最大长度 */
    private static final int MAX_REQUEST_ID_LENGTH = 64;

    private static final DateTimeFormatter ORDER_NO_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final CoffeeProductMapper productMapper;
    private final CoffeeSpecGroupMapper specGroupMapper;
    private final CoffeeSpecOptionMapper specOptionMapper;
    private final CoffeeProductSpecGroupMapper productSpecGroupMapper;
    private final CoffeeOrderMapper orderMapper;
    private final CoffeeOrderItemMapper orderItemMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final JsonMapper jsonMapper;
    private final PickupCodeGenerator pickupCodeGenerator;
    private final CouponService couponService;
    private final ActivityService activityService;
    private final SeckillResourceService seckillResourceService;
    private final CoffeeActivityMapper activityMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderCreateVO createOrder(Long userId, CreateOrderRequest request) {
        if (userId == null) {
            throw new BusinessException("登录状态已失效，请重新登录");
        }
        validateRequest(request);
        String requestId = normalizeRequestId(request.requestId());

        // 0. 幂等：同一客户端请求重试时直接返回已创建订单，避免重复扣库存
        if (requestId != null) {
            CoffeeOrder existingOrder = orderMapper.selectOne(Wrappers.<CoffeeOrder>lambdaQuery()
                    .eq(CoffeeOrder::getUserId, userId)
                    .eq(CoffeeOrder::getRequestId, requestId)
                    .last("LIMIT 1"));
            if (existingOrder != null) {
                log.info("下单幂等命中 userId={}, requestId={}, orderId={}",
                        userId, requestId, existingOrder.getId());
                return toOrderCreateVO(existingOrder);
            }
        }

        List<CreateOrderItemRequest> itemRequests = request.items();

        // 1. 商品 ID 去重后批量查库，商品信息与价格只以数据库为准
        Set<Long> productIds = itemRequests.stream()
                .map(CreateOrderItemRequest::productId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<CoffeeProduct> products = productMapper.selectList(
                Wrappers.<CoffeeProduct>lambdaQuery().in(CoffeeProduct::getId, productIds));
        Map<Long, CoffeeProduct> productMap = products.stream()
                .collect(Collectors.toMap(CoffeeProduct::getId, Function.identity()));
        if (productMap.size() != productIds.size()) {
            throw new BusinessException("部分商品不存在，请刷新后重新选择");
        }
        Set<Long> activeSeckillProductIds = activityService.listActiveSeckillProductIds();
        for (CoffeeProduct product : products) {
            if (product.getStatus() == null || product.getStatus() != PRODUCT_ON_SHELF) {
                throw new BusinessException("商品「" + product.getName() + "」已下架");
            }
            if (product.getStock() == null || product.getStock() <= 0) {
                throw new BusinessException("商品「" + product.getName() + "」已售罄");
            }
            if (activeSeckillProductIds.contains(product.getId())) {
                throw new BusinessException("商品「" + product.getName() + "」正在秒杀，请从秒杀活动入口下单");
            }
        }

        // 2. 查询商品可用的规格组绑定关系
        List<CoffeeProductSpecGroup> bindings = productSpecGroupMapper.selectList(
                Wrappers.<CoffeeProductSpecGroup>lambdaQuery()
                        .in(CoffeeProductSpecGroup::getProductId, productIds));
        Map<Long, List<Long>> productGroupIds = bindings.stream()
                .collect(Collectors.groupingBy(
                        CoffeeProductSpecGroup::getProductId,
                        Collectors.mapping(CoffeeProductSpecGroup::getGroupId, Collectors.toList())));

        // 3. 批量查询规格组，用于必选校验与名称快照
        Set<Long> groupIds = bindings.stream()
                .map(CoffeeProductSpecGroup::getGroupId)
                .collect(Collectors.toSet());
        Map<Long, CoffeeSpecGroup> groupMap = groupIds.isEmpty()
                ? Collections.emptyMap()
                : specGroupMapper.selectList(
                        Wrappers.<CoffeeSpecGroup>lambdaQuery().in(CoffeeSpecGroup::getId, groupIds))
                .stream()
                .collect(Collectors.toMap(CoffeeSpecGroup::getId, Function.identity()));

        // 4. 批量查询所有被选中的规格选项
        Set<Long> optionIds = itemRequests.stream()
                .flatMap(item -> item.optionIds() == null ? Stream.empty() : item.optionIds().stream())
                .collect(Collectors.toSet());
        Map<Long, CoffeeSpecOption> optionMap = optionIds.isEmpty()
                ? Collections.emptyMap()
                : specOptionMapper.selectList(
                        Wrappers.<CoffeeSpecOption>lambdaQuery().in(CoffeeSpecOption::getId, optionIds))
                .stream()
                .collect(Collectors.toMap(CoffeeSpecOption::getId, Function.identity()));
        if (optionMap.size() != optionIds.size()) {
            throw new BusinessException("部分规格选项不存在，请刷新后重新选择");
        }

        // 5. 逐条校验规格合法性并重算金额
        long totalAmount = 0L;
        int totalCount = 0;
        Map<Long, Long> subtotalByProduct = new LinkedHashMap<>();
        List<CoffeeOrderItem> orderItems = new ArrayList<>(itemRequests.size());
        for (CreateOrderItemRequest itemRequest : itemRequests) {
            CoffeeProduct product = productMap.get(itemRequest.productId());
            List<Long> selectedOptionIds = itemRequest.optionIds() == null
                    ? Collections.emptyList()
                    : itemRequest.optionIds();
            if (new HashSet<>(selectedOptionIds).size() != selectedOptionIds.size()) {
                throw new BusinessException("规格选项重复，请重新选择");
            }

            List<Long> boundGroupIds = productGroupIds.getOrDefault(product.getId(), Collections.emptyList());
            Set<Long> selectedGroupIds = new HashSet<>();
            long specExtraPrice = 0L;
            List<OrderItemSpecSnapshot> specSnapshots = new ArrayList<>(selectedOptionIds.size());

            for (Long optionId : selectedOptionIds) {
                CoffeeSpecOption option = optionMap.get(optionId);
                if (option.getStatus() == null || option.getStatus() != SPEC_OPTION_ENABLED) {
                    throw new BusinessException("规格「" + option.getName() + "」已停用，请重新选择");
                }
                if (!boundGroupIds.contains(option.getGroupId())) {
                    throw new BusinessException("商品「" + product.getName() + "」不支持所选规格");
                }
                CoffeeSpecGroup group = groupMap.get(option.getGroupId());
                if (group == null) {
                    throw new BusinessException("规格数据异常，请刷新后重试");
                }
                boolean firstSelectedInGroup = selectedGroupIds.add(group.getId());
                if (!firstSelectedInGroup && !isMultiple(group)) {
                    throw new BusinessException("「" + group.getName() + "」只能选择一项");
                }
                specExtraPrice += option.getExtraPrice();
                specSnapshots.add(new OrderItemSpecSnapshot(
                        group.getId(), group.getName(),
                        option.getId(), option.getName(),
                        option.getExtraPrice()));
            }

            // 必选规格组校验
            for (Long groupId : boundGroupIds) {
                CoffeeSpecGroup group = groupMap.get(groupId);
                if (group == null) {
                    continue;
                }
                if (isRequired(group) && !selectedGroupIds.contains(groupId)) {
                    throw new BusinessException("请选择「" + group.getName() + "」");
                }
            }

            // 金额重算：单件价 = 商品基础价 + 规格加价合计
            long unitPrice = product.getPrice() + specExtraPrice;
            long subtotalAmount = unitPrice * itemRequest.quantity();
            String specText = specSnapshots.stream()
                    .map(OrderItemSpecSnapshot::optionName)
                    .collect(Collectors.joining(" / "));

            CoffeeOrderItem orderItem = new CoffeeOrderItem();
            orderItem.setProductId(product.getId());
            orderItem.setProductName(product.getName());
            orderItem.setProductPic(product.getPicUrl());
            orderItem.setBasePrice(product.getPrice());
            orderItem.setSpecExtraPrice(specExtraPrice);
            orderItem.setSpecText(specText);
            orderItem.setUnitPrice(unitPrice);
            orderItem.setQuantity(itemRequest.quantity());
            orderItem.setSubtotalAmount(subtotalAmount);
            orderItem.setSpecJson(writeSpecJson(specSnapshots));
            orderItems.add(orderItem);

            totalAmount += subtotalAmount;
            totalCount += itemRequest.quantity();
            subtotalByProduct.merge(product.getId(), subtotalAmount, Long::sum);
        }

        // 6. 计算满减与优惠券：满减先算，券门槛按满减后的金额计算
        ActivityService.FullReductionResult fullReduction =
                activityService.calculateFullReduction(subtotalByProduct);
        long activityDiscount = fullReduction.discountAmount();
        long couponBaseAmount = Math.max(totalAmount - activityDiscount, 0L);
        long couponDiscount = couponService.calculateDiscount(userId, request.couponId(), couponBaseAmount);
        long discountAmount = activityDiscount + couponDiscount;
        long payAmount = Math.max(totalAmount - discountAmount, 0L);

        // 7. 按商品维度合并数量后原子扣减库存，库存不足会抛异常并回滚整个事务
        Map<Long, Integer> quantityByProduct = new LinkedHashMap<>();
        for (CreateOrderItemRequest itemRequest : itemRequests) {
            quantityByProduct.merge(itemRequest.productId(), itemRequest.quantity(), Integer::sum);
        }
        quantityByProduct.forEach((productId, quantity) -> {
            int updatedRows = productMapper.deductStock(productId, quantity);
            if (updatedRows == 0) {
                CoffeeProduct product = productMap.get(productId);
                throw new BusinessException("商品「" + product.getName() + "」库存不足，请减少数量后重试");
            }
        });

        // 8. 订单主表与明细在同一事务内原子落库，取餐码在支付成功后生成
        CoffeeOrder order = new CoffeeOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setStatus(OrderStatus.PENDING_PAY.name());
        order.setOrderType("NORMAL");
        order.setPickupType(request.pickupType());
        order.setTotalAmount(totalAmount);
        order.setDiscountAmount(discountAmount);
        order.setActivityDiscountAmount(activityDiscount);
        order.setActivityId(fullReduction.activityId());
        order.setCouponId(request.couponId());
        order.setRequestId(requestId);
        order.setPayAmount(payAmount);
        order.setItemCount(totalCount);
        order.setRemark(normalizeRemark(request.remark()));
        orderMapper.insert(order);

        for (CoffeeOrderItem orderItem : orderItems) {
            orderItem.setOrderId(order.getId());
            orderItemMapper.insert(orderItem);
        }

        // 9. 核销优惠券：条件更新失败说明优惠券已被其他订单占用，整体回滚
        if (request.couponId() != null) {
            couponService.markUsed(userId, request.couponId(), order.getId());
        }

        // 10. 发布状态变更事件，事务提交后由监听器异步写入流水（虚拟线程）
        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                order.getId(), null, order.getStatus(),
                userId, UserRole.CUSTOMER.name(), "创建订单"));

        log.info("订单创建成功 orderNo={}, userId={}, totalAmount={}分, activityDiscount={}分, couponDiscount={}分, payAmount={}分",
                order.getOrderNo(), userId, totalAmount, activityDiscount, couponDiscount, payAmount);
        return toOrderCreateVO(order);
    }

    /**
     * 支付成功统一处理器：Mock 支付确认与真实微信支付回调共用
     * 只有这里可以把订单从待支付推进到制作中
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmPaymentSuccess(Long orderId, String payChannel,
                                      String transactionId, long paidAmount) {
        if (orderId == null || StrUtil.isBlank(payChannel) || StrUtil.isBlank(transactionId)) {
            throw new BusinessException("支付回调参数不完整");
        }
        CoffeeOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (OrderStatus.CANCELLED.name().equals(order.getStatus())) {
            throw new BusinessException("订单已取消，无法支付");
        }
        // 幂等：重复回调直接返回，避免重复推进状态
        if (OrderStatus.MAKING.name().equals(order.getStatus())
                || OrderStatus.READY.name().equals(order.getStatus())
                || OrderStatus.COMPLETED.name().equals(order.getStatus())) {
            log.info("支付回调幂等命中 orderId={}, status={}, transactionId={}",
                    orderId, order.getStatus(), transactionId);
            return;
        }
        if (!OrderStatus.PENDING_PAY.name().equals(order.getStatus())) {
            throw new BusinessException("当前订单状态不允许支付");
        }
        if (order.getPayAmount() == null || order.getPayAmount() != paidAmount) {
            throw new BusinessException("支付金额与订单金额不一致");
        }

        String pickupCode = StrUtil.isBlank(order.getPickupCode())
                ? pickupCodeGenerator.nextPickupCode()
                : order.getPickupCode();
        CoffeeOrder updateOrder = new CoffeeOrder();
        updateOrder.setStatus(OrderStatus.MAKING.name());
        updateOrder.setPayTime(LocalDateTime.now());
        updateOrder.setPickupCode(pickupCode);
        updateOrder.setPayChannel(payChannel);
        updateOrder.setTransactionId(transactionId);
        int updatedRows = orderMapper.update(updateOrder, Wrappers.<CoffeeOrder>lambdaUpdate()
                .eq(CoffeeOrder::getId, orderId)
                .eq(CoffeeOrder::getStatus, OrderStatus.PENDING_PAY.name()));
        if (updatedRows == 0) {
            CoffeeOrder latest = orderMapper.selectById(orderId);
            if (latest != null && !OrderStatus.PENDING_PAY.name().equals(latest.getStatus())) {
                log.info("支付回调并发幂等命中 orderId={}, status={}", orderId, latest.getStatus());
                return;
            }
            throw new BusinessException("订单状态已变化，请刷新后重试");
        }

        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                orderId, OrderStatus.PENDING_PAY.name(), OrderStatus.MAKING.name(),
                order.getUserId(), UserRole.CUSTOMER.name(),
                "支付成功（" + payChannel + "）"));
        log.info("订单支付成功 orderId={}, payChannel={}, transactionId={}, pickupCode={}",
                orderId, payChannel, transactionId, pickupCode);
    }

    @Override
    public List<OrderVO> listMyOrders(Long userId, String status) {
        if (StrUtil.isNotBlank(status) && !OrderStatus.isValid(status)) {
            throw new BusinessException("订单状态不合法");
        }
        List<CoffeeOrder> orders = orderMapper.selectList(Wrappers.<CoffeeOrder>lambdaQuery()
                .eq(CoffeeOrder::getUserId, userId)
                .eq(StrUtil.isNotBlank(status), CoffeeOrder::getStatus, status)
                .orderByDesc(CoffeeOrder::getCreateTime)
                .last("LIMIT 50"));
        return buildOrderVOs(orders);
    }

    @Override
    public OrderVO getOrderDetail(Long userId, Long orderId) {
        CoffeeOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!Objects.equals(order.getUserId(), userId)) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "无权查看该订单");
        }
        return buildOrderVOs(List.of(order)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long userId, Long orderId) {
        CoffeeOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!Objects.equals(order.getUserId(), userId)) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "无权操作该订单");
        }
        // 幂等：重复取消直接返回
        if (OrderStatus.CANCELLED.name().equals(order.getStatus())) {
            return;
        }
        if (!OrderStatus.PENDING_PAY.name().equals(order.getStatus())) {
            throw new BusinessException("订单已支付，请联系店长处理");
        }

        CoffeeOrder updateOrder = new CoffeeOrder();
        updateOrder.setStatus(OrderStatus.CANCELLED.name());
        updateOrder.setCancelTime(LocalDateTime.now());
        int updatedRows = orderMapper.update(updateOrder, Wrappers.<CoffeeOrder>lambdaUpdate()
                .eq(CoffeeOrder::getId, orderId)
                .eq(CoffeeOrder::getStatus, OrderStatus.PENDING_PAY.name()));
        if (updatedRows == 0) {
            throw new BusinessException("订单状态已变化，请刷新后重试");
        }

        // 回滚库存与优惠券
        restoreOrderResources(order);

        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                orderId, OrderStatus.PENDING_PAY.name(), OrderStatus.CANCELLED.name(),
                userId, UserRole.CUSTOMER.name(), "顾客取消订单"));
        log.info("订单已取消 orderId={}, userId={}", orderId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelExpiredOrder(Long orderId, LocalDateTime cutoff) {
        CoffeeOrder order = orderMapper.selectById(orderId);
        if (order == null
                || !OrderStatus.PENDING_PAY.name().equals(order.getStatus())
                || order.getCreateTime() == null
                || !order.getCreateTime().isBefore(cutoff)) {
            return false;
        }

        CoffeeOrder updateOrder = new CoffeeOrder();
        updateOrder.setStatus(OrderStatus.CANCELLED.name());
        updateOrder.setCancelTime(LocalDateTime.now());
        int updatedRows = orderMapper.update(updateOrder, Wrappers.<CoffeeOrder>lambdaUpdate()
                .eq(CoffeeOrder::getId, orderId)
                .eq(CoffeeOrder::getStatus, OrderStatus.PENDING_PAY.name())
                .lt(CoffeeOrder::getCreateTime, cutoff));
        if (updatedRows == 0) {
            return false;
        }

        restoreOrderResources(order);
        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                orderId, OrderStatus.PENDING_PAY.name(), OrderStatus.CANCELLED.name(),
                null, "SYSTEM", "超时未支付自动取消"));
        log.info("超时订单已自动关闭 orderId={}", orderId);
        return true;
    }

    @Override
    public void restoreResourcesForCancelledOrder(Long orderId) {
        if (orderId == null) {
            return;
        }
        CoffeeOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            log.warn("回滚订单资源失败，订单不存在 orderId={}", orderId);
            return;
        }
        restoreOrderResources(order);
    }

    /**
     * 取消订单时恢复库存与优惠券
     */
    private void restoreOrderResources(CoffeeOrder order) {
        List<CoffeeOrderItem> items = orderItemMapper.selectList(
                Wrappers.<CoffeeOrderItem>lambdaQuery().eq(CoffeeOrderItem::getOrderId, order.getId()));
        Map<Long, Integer> quantityByProduct = new LinkedHashMap<>();
        for (CoffeeOrderItem item : items) {
            quantityByProduct.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }
        quantityByProduct.forEach(productMapper::restoreStock);
        if (order.getCouponId() != null) {
            couponService.restore(order.getCouponId(), order.getId());
        }
        // 秒杀订单：回滚活动销量，事务提交后再恢复 Redis 库存与用户限购
        seckillResourceService.restoreForCancelledOrder(order, items);
    }

    private OrderCreateVO toOrderCreateVO(CoffeeOrder order) {
        return new OrderCreateVO(order.getId(), order.getOrderNo(), order.getPickupCode(),
                order.getPickupType(), order.getTotalAmount(), order.getDiscountAmount(),
                order.getPayAmount(), order.getItemCount(), order.getStatus());
    }

    /**
     * 批量组装顾客端订单视图，避免逐单查询明细
     */
    private List<OrderVO> buildOrderVOs(List<CoffeeOrder> orders) {
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

        Map<Long, CoffeeActivity> activityMap = loadActivityMap(orders);
        return orders.stream()
                .map(order -> {
                    CoffeeActivity activity = order.getActivityId() == null
                            ? null : activityMap.get(order.getActivityId());
                    return new OrderVO(
                            order.getId(),
                            order.getOrderNo(),
                            order.getOrderType() == null ? "NORMAL" : order.getOrderType(),
                            order.getPickupCode(),
                            order.getPickupType(),
                            order.getStatus(),
                            order.getTotalAmount(),
                            order.getDiscountAmount(),
                            order.getActivityDiscountAmount() == null
                                    ? 0L : order.getActivityDiscountAmount(),
                            order.getPayAmount(),
                            order.getItemCount(),
                            order.getRemark(),
                            order.getActivityId(),
                            activity == null ? null : activity.getTitle(),
                            order.getCreateTime(),
                            order.getPayTime(),
                            itemsByOrder.getOrDefault(order.getId(), Collections.emptyList())
                                    .stream()
                                    .map(item -> new OrderItemVO(
                                            item.getProductId(),
                                            item.getActivityProductId(),
                                            item.getProductName(),
                                            item.getProductPic(),
                                            item.getSpecText(),
                                            item.getQuantity(),
                                            item.getUnitPrice(),
                                            item.getSubtotalAmount()))
                                    .toList());
                })
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

    /**
     * 基础参数校验：只校验结构与数量，不涉及金额
     */
    private void validateRequest(CreateOrderRequest request) {
        if (request == null || CollectionUtils.isEmpty(request.items())) {
            throw new BusinessException("订单商品不能为空");
        }
        if (request.items().size() > MAX_ITEM_TYPES) {
            throw new BusinessException("单次下单商品种类不能超过 " + MAX_ITEM_TYPES + " 种");
        }
        if (request.remark() != null && request.remark().length() > MAX_REMARK_LENGTH) {
            throw new BusinessException("备注不能超过 " + MAX_REMARK_LENGTH + " 个字符");
        }
        if (!PickupType.isValid(request.pickupType())) {
            throw new BusinessException("取餐方式不合法");
        }
        if (request.requestId() != null && request.requestId().trim().length() > MAX_REQUEST_ID_LENGTH) {
            throw new BusinessException("请求标识过长");
        }
        for (CreateOrderItemRequest item : request.items()) {
            if (item == null || item.productId() == null) {
                throw new BusinessException("商品 ID 不能为空");
            }
            if (item.quantity() == null
                    || item.quantity() < MIN_QUANTITY
                    || item.quantity() > MAX_QUANTITY) {
                throw new BusinessException("商品数量需在 " + MIN_QUANTITY + "~" + MAX_QUANTITY + " 之间");
            }
            if (item.optionIds() != null && item.optionIds().stream().anyMatch(Objects::isNull)) {
                throw new BusinessException("规格选项不能为空");
            }
        }
    }

    private String normalizeRequestId(String requestId) {
        if (StrUtil.isBlank(requestId)) {
            return null;
        }
        return requestId.trim();
    }

    private boolean isRequired(CoffeeSpecGroup group) {
        return group.getRequired() != null && group.getRequired() == SPEC_GROUP_REQUIRED;
    }

    private boolean isMultiple(CoffeeSpecGroup group) {
        return group.getMultiple() != null && group.getMultiple() == SPEC_GROUP_MULTIPLE;
    }

    private String writeSpecJson(List<OrderItemSpecSnapshot> specSnapshots) {
        try {
            return jsonMapper.writeValueAsString(specSnapshots);
        } catch (Exception exception) {
            throw new IllegalStateException("规格快照序列化失败", exception);
        }
    }

    private String normalizeRemark(String remark) {
        return remark == null ? "" : remark.trim();
    }

    /**
     * 订单号：17 位时间戳 + 4 位随机数，配合 uk_order_no 唯一索引兜底
     */
    private String generateOrderNo() {
        return LocalDateTime.now().format(ORDER_NO_TIME_FORMATTER)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}

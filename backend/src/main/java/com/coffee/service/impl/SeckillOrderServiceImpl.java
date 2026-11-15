package com.coffee.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.coffee.common.BusinessException;
import com.coffee.dto.order.CreateSeckillOrderRequest;
import com.coffee.dto.order.OrderItemSpecSnapshot;
import com.coffee.dto.order.SeckillOrderContext;
import com.coffee.entity.CoffeeActivity;
import com.coffee.entity.CoffeeActivityProduct;
import com.coffee.entity.CoffeeOrder;
import com.coffee.entity.CoffeeProduct;
import com.coffee.entity.CoffeeProductSpecGroup;
import com.coffee.entity.CoffeeSpecGroup;
import com.coffee.entity.CoffeeSpecOption;
import com.coffee.enums.ActivityType;
import com.coffee.enums.PickupType;
import com.coffee.mapper.CoffeeActivityMapper;
import com.coffee.mapper.CoffeeActivityProductMapper;
import com.coffee.mapper.CoffeeOrderMapper;
import com.coffee.mapper.CoffeeProductMapper;
import com.coffee.mapper.CoffeeProductSpecGroupMapper;
import com.coffee.mapper.CoffeeSpecGroupMapper;
import com.coffee.mapper.CoffeeSpecOptionMapper;
import com.coffee.service.SeckillOrderService;
import com.coffee.service.SeckillStockRepository;
import com.coffee.vo.order.OrderCreateVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 秒杀下单编排
 * 顺序：DB 幂等 -> Redis 请求锁 -> 参数与活动校验 -> Redis 原子预扣 -> DB 事务 -> 失败补偿
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillOrderServiceImpl implements SeckillOrderService {

    private static final int MIN_QUANTITY = 1;
    private static final int MAX_QUANTITY = 99;
    private static final int MAX_REMARK_LENGTH = 200;
    private static final int MAX_REQUEST_ID_LENGTH = 64;
    private static final int PRODUCT_ON_SHELF = 1;
    private static final int ACTIVITY_OPEN = 1;
    private static final int BINDING_ENABLED = 1;
    private static final int SPEC_OPTION_ENABLED = 1;
    private static final int SPEC_GROUP_REQUIRED = 1;
    private static final int SPEC_GROUP_MULTIPLE = 1;
    private static final Duration REQUEST_LOCK_TTL = Duration.ofSeconds(120);

    private final CoffeeActivityProductMapper activityProductMapper;
    private final CoffeeActivityMapper activityMapper;
    private final CoffeeProductMapper productMapper;
    private final CoffeeProductSpecGroupMapper productSpecGroupMapper;
    private final CoffeeSpecGroupMapper specGroupMapper;
    private final CoffeeSpecOptionMapper specOptionMapper;
    private final CoffeeOrderMapper orderMapper;
    private final SeckillStockRepository seckillStockRepository;
    private final SeckillOrderTxService seckillOrderTxService;
    private final JsonMapper jsonMapper;

    @Override
    public OrderCreateVO createSeckillOrder(Long userId, CreateSeckillOrderRequest request) {
        if (userId == null) {
            throw new BusinessException("登录状态已失效，请重新登录");
        }
        validateRequest(request);
        String requestId = request.requestId().trim();

        // 1. DB 幂等：响应丢失后的重试直接返回已有订单，不再扣减 Redis
        CoffeeOrder existingOrder = findOrderByRequestId(userId, requestId);
        if (existingOrder != null) {
            log.info("秒杀下单幂等命中 userId={}, requestId={}, orderId={}",
                    userId, requestId, existingOrder.getId());
            return toOrderCreateVO(existingOrder);
        }

        // 2. 请求锁：拦截同一 requestId 的并发重试
        if (!seckillStockRepository.acquireRequestLock(userId, requestId, REQUEST_LOCK_TTL)) {
            throw new BusinessException("请求处理中，请勿重复提交");
        }

        boolean deducted = false;
        Duration stockTtl = null;
        try {
            // 3. 校验活动、商品与规格
            CoffeeActivityProduct binding = activityProductMapper.selectById(request.activityProductId());
            if (binding == null || binding.getStatus() == null || binding.getStatus() != BINDING_ENABLED) {
                throw new BusinessException("活动商品已停用");
            }
            CoffeeActivity activity = activityMapper.selectById(binding.getActivityId());
            validateActivity(activity);

            CoffeeProduct product = productMapper.selectById(binding.getProductId());
            if (product == null) {
                throw new BusinessException("活动商品不存在");
            }
            if (product.getStatus() == null || product.getStatus() != PRODUCT_ON_SHELF) {
                throw new BusinessException("活动商品已下架");
            }
            if (binding.getActivityPrice() == null || binding.getActivityPrice() <= 0) {
                throw new BusinessException("活动价格异常，请稍后重试");
            }
            if (product.getPrice() != null && binding.getActivityPrice() >= product.getPrice()) {
                throw new BusinessException("活动价格异常，请稍后重试");
            }
            int limitPerUser = binding.getLimitPerUser() == null ? 1 : binding.getLimitPerUser();
            if (request.quantity() > limitPerUser) {
                throw new BusinessException("超过每人限购数量");
            }

            PricedItem pricedItem = priceItem(product, request.optionIds());
            long unitPrice = binding.getActivityPrice() + pricedItem.specExtraPrice();
            long totalAmount = unitPrice * request.quantity();

            // 4. Redis 原子预扣活动库存与用户限购
            stockTtl = stockTtl(activity.getEndTime());
            long remaining = seckillStockRepository.deduct(
                    binding.getId(), userId, request.quantity(), limitPerUser, stockTtl);
            if (remaining == SeckillStockRepository.NOT_PREHEATED) {
                throw new BusinessException("活动库存尚未就绪，请稍后重试");
            }
            if (remaining == SeckillStockRepository.LIMIT_EXCEEDED) {
                throw new BusinessException("超过每人限购数量");
            }
            if (remaining == SeckillStockRepository.SOLD_OUT) {
                throw new BusinessException("活动商品已抢完");
            }
            deducted = true;

            // 5. DB 事务落库
            SeckillOrderContext context = new SeckillOrderContext(
                    userId,
                    activity.getId(),
                    binding.getId(),
                    product.getId(),
                    product.getName(),
                    product.getPicUrl(),
                    product.getPrice(),
                    binding.getActivityPrice(),
                    pricedItem.specExtraPrice(),
                    pricedItem.specText(),
                    pricedItem.specJson(),
                    request.quantity(),
                    unitPrice,
                    totalAmount,
                    request.pickupType(),
                    normalizeRemark(request.remark()),
                    requestId);
            return seckillOrderTxService.createSeckillOrder(context);
        } catch (DuplicateKeyException exception) {
            if (deducted && stockTtl != null) {
                rollbackQuietly(request.activityProductId(), userId, request.quantity(), stockTtl);
            }
            CoffeeOrder duplicatedOrder = findOrderByRequestId(userId, requestId);
            if (duplicatedOrder != null) {
                return toOrderCreateVO(duplicatedOrder);
            }
            throw exception;
        } catch (RuntimeException exception) {
            if (deducted && stockTtl != null) {
                rollbackQuietly(request.activityProductId(), userId, request.quantity(), stockTtl);
            }
            throw exception;
        } finally {
            try {
                seckillStockRepository.releaseRequestLock(userId, requestId);
            } catch (Exception exception) {
                log.warn("释放秒杀请求锁失败 userId={}, requestId={}", userId, requestId, exception);
            }
        }
    }

    private void validateActivity(CoffeeActivity activity) {
        if (activity == null || !ActivityType.SECKILL.name().equals(activity.getType())) {
            throw new BusinessException("秒杀活动不存在");
        }
        if (activity.getIsOpen() == null || activity.getIsOpen() != ACTIVITY_OPEN) {
            throw new BusinessException("活动已关闭");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getStartTime())) {
            throw new BusinessException("活动尚未开始");
        }
        if (now.isAfter(activity.getEndTime())) {
            throw new BusinessException("活动已结束");
        }
    }

    /**
     * 校验规格并计算规格加价与快照
     */
    private PricedItem priceItem(CoffeeProduct product, List<Long> optionIds) {
        List<Long> selectedOptionIds = optionIds == null ? Collections.emptyList() : optionIds;
        if (new HashSet<>(selectedOptionIds).size() != selectedOptionIds.size()) {
            throw new BusinessException("规格选项重复，请重新选择");
        }

        List<CoffeeProductSpecGroup> bindings = productSpecGroupMapper.selectList(
                Wrappers.<CoffeeProductSpecGroup>lambdaQuery()
                        .eq(CoffeeProductSpecGroup::getProductId, product.getId()));
        List<Long> boundGroupIds = bindings.stream()
                .map(CoffeeProductSpecGroup::getGroupId)
                .toList();
        Set<Long> boundGroupIdSet = new HashSet<>(boundGroupIds);

        Map<Long, CoffeeSpecGroup> groupMap = boundGroupIds.isEmpty()
                ? Collections.emptyMap()
                : specGroupMapper.selectByIds(boundGroupIds).stream()
                .collect(Collectors.toMap(CoffeeSpecGroup::getId, Function.identity()));

        Map<Long, CoffeeSpecOption> optionMap = selectedOptionIds.isEmpty()
                ? Collections.emptyMap()
                : specOptionMapper.selectByIds(selectedOptionIds).stream()
                .collect(Collectors.toMap(CoffeeSpecOption::getId, Function.identity()));
        if (optionMap.size() != selectedOptionIds.size()) {
            throw new BusinessException("部分规格选项不存在，请刷新后重新选择");
        }

        long specExtraPrice = 0L;
        Set<Long> selectedGroupIds = new HashSet<>();
        List<OrderItemSpecSnapshot> snapshots = new ArrayList<>(selectedOptionIds.size());
        for (Long optionId : selectedOptionIds) {
            CoffeeSpecOption option = optionMap.get(optionId);
            if (option.getStatus() == null || option.getStatus() != SPEC_OPTION_ENABLED) {
                throw new BusinessException("规格「" + option.getName() + "」已停用，请重新选择");
            }
            if (!boundGroupIdSet.contains(option.getGroupId())) {
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
            specExtraPrice += option.getExtraPrice() == null ? 0L : option.getExtraPrice();
            snapshots.add(new OrderItemSpecSnapshot(
                    group.getId(), group.getName(),
                    option.getId(), option.getName(),
                    option.getExtraPrice()));
        }

        for (Long groupId : boundGroupIds) {
            CoffeeSpecGroup group = groupMap.get(groupId);
            if (group != null && isRequired(group) && !selectedGroupIds.contains(groupId)) {
                throw new BusinessException("请选择「" + group.getName() + "」");
            }
        }

        String specText = snapshots.stream()
                .map(OrderItemSpecSnapshot::optionName)
                .collect(Collectors.joining(" / "));
        return new PricedItem(specExtraPrice, specText, writeSpecJson(snapshots));
    }

    private void validateRequest(CreateSeckillOrderRequest request) {
        if (request == null || request.activityProductId() == null) {
            throw new BusinessException("请选择秒杀商品");
        }
        if (request.quantity() == null
                || request.quantity() < MIN_QUANTITY
                || request.quantity() > MAX_QUANTITY) {
            throw new BusinessException("购买数量需在 " + MIN_QUANTITY + "~" + MAX_QUANTITY + " 之间");
        }
        if (!PickupType.isValid(request.pickupType())) {
            throw new BusinessException("取餐方式不合法");
        }
        if (request.remark() != null && request.remark().length() > MAX_REMARK_LENGTH) {
            throw new BusinessException("备注不能超过 " + MAX_REMARK_LENGTH + " 个字符");
        }
        if (StrUtil.isBlank(request.requestId())) {
            throw new BusinessException("请求标识不能为空");
        }
        if (request.requestId().trim().length() > MAX_REQUEST_ID_LENGTH) {
            throw new BusinessException("请求标识过长");
        }
        if (request.optionIds() != null && request.optionIds().stream().anyMatch(java.util.Objects::isNull)) {
            throw new BusinessException("规格选项不能为空");
        }
    }

    private CoffeeOrder findOrderByRequestId(Long userId, String requestId) {
        return orderMapper.selectOne(Wrappers.<CoffeeOrder>lambdaQuery()
                .eq(CoffeeOrder::getUserId, userId)
                .eq(CoffeeOrder::getRequestId, requestId)
                .last("LIMIT 1"));
    }

    private Duration stockTtl(LocalDateTime endTime) {
        return Duration.between(LocalDateTime.now(), endTime.plusDays(1));
    }

    private void rollbackQuietly(Long activityProductId, Long userId, int quantity, Duration ttl) {
        try {
            seckillStockRepository.rollback(activityProductId, userId, quantity, ttl);
        } catch (Exception exception) {
            log.error("秒杀 Redis 库存回滚失败，需人工对账 activityProductId={}, userId={}, quantity={}",
                    activityProductId, userId, quantity, exception);
        }
    }

    private String normalizeRemark(String remark) {
        return remark == null ? "" : remark.trim();
    }

    private boolean isRequired(CoffeeSpecGroup group) {
        return group.getRequired() != null && group.getRequired() == SPEC_GROUP_REQUIRED;
    }

    private boolean isMultiple(CoffeeSpecGroup group) {
        return group.getMultiple() != null && group.getMultiple() == SPEC_GROUP_MULTIPLE;
    }

    private String writeSpecJson(List<OrderItemSpecSnapshot> snapshots) {
        try {
            return jsonMapper.writeValueAsString(snapshots);
        } catch (Exception exception) {
            throw new IllegalStateException("规格快照序列化失败", exception);
        }
    }

    private OrderCreateVO toOrderCreateVO(CoffeeOrder order) {
        return new OrderCreateVO(
                order.getId(),
                order.getOrderNo(),
                order.getPickupCode(),
                order.getPickupType(),
                order.getTotalAmount(),
                order.getDiscountAmount(),
                order.getPayAmount(),
                order.getItemCount(),
                order.getStatus());
    }

    private record PricedItem(long specExtraPrice, String specText, String specJson) {
    }
}

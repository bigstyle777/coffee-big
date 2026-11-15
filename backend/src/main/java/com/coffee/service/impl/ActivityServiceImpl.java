package com.coffee.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.coffee.common.BusinessException;
import com.coffee.dto.admin.BindActivityProductRequest;
import com.coffee.dto.admin.SaveActivityRequest;
import com.coffee.dto.admin.UpdateActivityProductRequest;
import com.coffee.entity.CoffeeActivity;
import com.coffee.entity.CoffeeActivityProduct;
import com.coffee.entity.CoffeeProduct;
import com.coffee.entity.CoffeeProductSpecGroup;
import com.coffee.entity.CoffeeSpecGroup;
import com.coffee.entity.CoffeeSpecOption;
import com.coffee.entity.CoffeeOrder;
import com.coffee.enums.ActivityStatus;
import com.coffee.enums.ActivityType;
import com.coffee.mapper.CoffeeActivityMapper;
import com.coffee.mapper.CoffeeActivityProductMapper;
import com.coffee.mapper.CoffeeOrderMapper;
import com.coffee.mapper.CoffeeProductMapper;
import com.coffee.mapper.CoffeeProductSpecGroupMapper;
import com.coffee.mapper.CoffeeSpecGroupMapper;
import com.coffee.mapper.CoffeeSpecOptionMapper;
import com.coffee.service.ActivityService;
import com.coffee.service.SeckillStockRepository;
import com.coffee.vo.activity.SeckillActivityVO;
import com.coffee.vo.activity.SeckillProductVO;
import com.coffee.vo.admin.AdminActivityDetailVO;
import com.coffee.vo.admin.AdminActivityProductVO;
import com.coffee.vo.admin.AdminActivityVO;
import com.coffee.vo.admin.PageVO;
import com.coffee.vo.menu.MenuSpecGroupVO;
import com.coffee.vo.menu.MenuSpecOptionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 营销活动后台管理实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityServiceImpl implements ActivityService {

    private static final long MAX_PAGE_SIZE = 100L;
    private static final int MAX_TITLE_LENGTH = 64;
    private static final int MAX_REMARK_LENGTH = 255;
    private static final int BINDING_ENABLED = 1;
    private static final int BINDING_DISABLED = 0;
    private static final int PRODUCT_ON_SHELF = 1;
    private static final int ACTIVITY_OPEN = 1;
    private static final int ACTIVITY_CLOSED = 0;
    private static final long MIN_PREHEAT_TTL_MINUTES = 30L;

    private final CoffeeActivityMapper activityMapper;
    private final CoffeeActivityProductMapper activityProductMapper;
    private final CoffeeProductMapper productMapper;
    private final CoffeeProductSpecGroupMapper productSpecGroupMapper;
    private final CoffeeSpecGroupMapper specGroupMapper;
    private final CoffeeSpecOptionMapper specOptionMapper;
    private final CoffeeOrderMapper orderMapper;
    private final SeckillStockRepository seckillStockRepository;

    @Override
    public PageVO<AdminActivityVO> pageActivities(long pageNum, long pageSize, String keyword,
                                                  String type, String status, Integer isOpen) {
        long safePageNum = Math.max(pageNum, 1);
        long safePageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        if (StrUtil.isNotBlank(type) && !ActivityType.isValid(type)) {
            throw new BusinessException("活动类型不合法");
        }
        if (StrUtil.isNotBlank(status) && !ActivityStatus.isValid(status)) {
            throw new BusinessException("活动状态不合法");
        }

        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<CoffeeActivity> wrapper = Wrappers.<CoffeeActivity>lambdaQuery()
                .like(StrUtil.isNotBlank(keyword), CoffeeActivity::getTitle,
                        keyword == null ? null : keyword.trim())
                .eq(StrUtil.isNotBlank(type), CoffeeActivity::getType, type)
                .eq(isOpen != null, CoffeeActivity::getIsOpen, isOpen);

        if (StrUtil.isNotBlank(status)) {
            switch (status) {
                case "CLOSED" -> wrapper.eq(CoffeeActivity::getIsOpen, ACTIVITY_CLOSED);
                case "NOT_STARTED" -> wrapper.eq(CoffeeActivity::getIsOpen, ACTIVITY_OPEN)
                        .gt(CoffeeActivity::getStartTime, now);
                case "RUNNING" -> wrapper.eq(CoffeeActivity::getIsOpen, ACTIVITY_OPEN)
                        .le(CoffeeActivity::getStartTime, now)
                        .ge(CoffeeActivity::getEndTime, now);
                case "ENDED" -> wrapper.eq(CoffeeActivity::getIsOpen, ACTIVITY_OPEN)
                        .lt(CoffeeActivity::getEndTime, now);
                default -> throw new BusinessException("活动状态不合法");
            }
        }

        long total = activityMapper.selectCount(wrapper);
        if (total == 0) {
            return PageVO.empty(safePageNum, safePageSize);
        }
        long offset = (safePageNum - 1) * safePageSize;
        List<CoffeeActivity> activities = activityMapper.selectList(wrapper
                .orderByDesc(CoffeeActivity::getCreateTime)
                .orderByDesc(CoffeeActivity::getId)
                .last("LIMIT " + safePageSize + " OFFSET " + offset));
        Map<Long, List<CoffeeActivityProduct>> bindingsByActivity = loadBindings(
                activities.stream().map(CoffeeActivity::getId).toList());

        List<AdminActivityVO> records = activities.stream()
                .map(activity -> toActivityVO(
                        activity,
                        bindingsByActivity.getOrDefault(activity.getId(), Collections.emptyList()),
                        now))
                .toList();
        return new PageVO<>(total, safePageNum, safePageSize, records);
    }

    @Override
    public AdminActivityDetailVO getActivityDetail(Long activityId) {
        CoffeeActivity activity = getExistingActivity(activityId);
        List<CoffeeActivityProduct> bindings = activityProductMapper.selectList(
                Wrappers.<CoffeeActivityProduct>lambdaQuery()
                        .eq(CoffeeActivityProduct::getActivityId, activityId)
                        .orderByAsc(CoffeeActivityProduct::getSort)
                        .orderByAsc(CoffeeActivityProduct::getId));
        Map<Long, CoffeeProduct> productMap = loadProducts(bindings.stream()
                .map(CoffeeActivityProduct::getProductId)
                .toList());

        List<AdminActivityProductVO> products = bindings.stream()
                .map(binding -> toProductVO(activity, binding, productMap.get(binding.getProductId())))
                .toList();
        boolean allPreheated = resolveAllPreheated(activity, products);
        String status = ActivityStatus.resolve(activity.getIsOpen(), activity.getStartTime(),
                activity.getEndTime(), LocalDateTime.now()).name();
        return new AdminActivityDetailVO(
                activity.getId(),
                activity.getTitle(),
                activity.getType(),
                activity.getStartTime(),
                activity.getEndTime(),
                activity.getIsOpen(),
                status,
                activity.getFullAmount(),
                activity.getReduceAmount(),
                activity.getRemark(),
                allPreheated,
                products);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createActivity(SaveActivityRequest request) {
        validateSaveRequest(request);
        CoffeeActivity activity = new CoffeeActivity();
        applySaveRequest(activity, request);
        activity.setIsOpen(ACTIVITY_CLOSED);
        activityMapper.insert(activity);
        log.info("活动创建成功 activityId={}, title={}, type={}",
                activity.getId(), activity.getTitle(), activity.getType());
        return activity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateActivity(Long activityId, SaveActivityRequest request) {
        CoffeeActivity activity = getExistingActivity(activityId);
        validateSaveRequest(request);
        if (!Objects.equals(activity.getType(), request.type())) {
            throw new BusinessException("活动类型创建后不能修改");
        }
        boolean hasOrders = orderMapper.selectCount(Wrappers.<CoffeeOrder>lambdaQuery()
                .eq(CoffeeOrder::getActivityId, activityId)) > 0;
        if (hasOrders && !Objects.equals(activity.getStartTime(), request.startTime())) {
            throw new BusinessException("活动已产生订单，不能修改开始时间");
        }

        applySaveRequest(activity, request);
        activityMapper.updateById(activity);
        log.info("活动编辑成功 activityId={}, title={}", activityId, activity.getTitle());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOpen(Long activityId, Boolean isOpen) {
        if (isOpen == null) {
            throw new BusinessException("请选择开启或关闭活动");
        }
        CoffeeActivity activity = getExistingActivity(activityId);
        int targetOpen = isOpen ? ACTIVITY_OPEN : ACTIVITY_CLOSED;
        if (Objects.equals(activity.getIsOpen(), targetOpen)) {
            return;
        }
        if (isOpen) {
            if (!activity.getEndTime().isAfter(LocalDateTime.now())) {
                throw new BusinessException("活动已结束，不能开启");
            }
            if (ActivityType.SECKILL.name().equals(activity.getType())) {
                validateSeckillOpen(activity);
            } else {
                validateFullReduction(activity);
            }
        }

        CoffeeActivity updateActivity = new CoffeeActivity();
        updateActivity.setId(activityId);
        updateActivity.setIsOpen(targetOpen);
        activityMapper.updateById(updateActivity);
        log.info("活动开关更新成功 activityId={}, isOpen={}", activityId, targetOpen);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindProduct(Long activityId, BindActivityProductRequest request) {
        CoffeeActivity activity = getExistingActivity(activityId);
        requireClosed(activity);
        if (request == null || request.productId() == null) {
            throw new BusinessException("请选择商品");
        }

        CoffeeProduct product = productMapper.selectById(request.productId());
        if (product == null) {
            throw new BusinessException("商品不存在");
        }
        if (product.getStatus() == null || product.getStatus() != PRODUCT_ON_SHELF) {
            throw new BusinessException("商品未上架，不能绑定活动");
        }

        CoffeeActivityProduct existing = activityProductMapper.selectOne(
                Wrappers.<CoffeeActivityProduct>lambdaQuery()
                        .eq(CoffeeActivityProduct::getActivityId, activityId)
                        .eq(CoffeeActivityProduct::getProductId, request.productId()));
        if (existing != null && existing.getStatus() != null
                && existing.getStatus() == BINDING_ENABLED) {
            throw new BusinessException("该商品已绑定当前活动");
        }

        boolean seckill = ActivityType.SECKILL.name().equals(activity.getType());
        int soldCount = existing == null || existing.getSoldCount() == null
                ? 0 : existing.getSoldCount();
        if (seckill) {
            validateSeckillBinding(request.activityPrice(), request.activityStock(),
                    request.limitPerUser(), product, soldCount);
        }

        CoffeeActivityProduct binding = existing == null ? new CoffeeActivityProduct() : existing;
        binding.setActivityId(activityId);
        binding.setProductId(request.productId());
        binding.setActivityPrice(seckill ? request.activityPrice() : 0L);
        binding.setActivityStock(seckill ? request.activityStock() : 0);
        binding.setLimitPerUser(seckill ? request.limitPerUser() : 1);
        binding.setStatus(BINDING_ENABLED);
        if (existing == null) {
            binding.setSoldCount(0);
            binding.setSort(nextBindingSort(activityId));
            activityProductMapper.insert(binding);
        } else {
            activityProductMapper.updateById(binding);
            clearSeckillStock(activity, binding.getId());
        }
        log.info("活动商品绑定成功 activityId={}, productId={}, seckill={}",
                activityId, request.productId(), seckill);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateActivityProduct(Long activityId, Long bindingId,
                                      UpdateActivityProductRequest request) {
        CoffeeActivity activity = getExistingActivity(activityId);
        requireClosed(activity);
        if (!ActivityType.SECKILL.name().equals(activity.getType())) {
            throw new BusinessException("满减活动无需设置活动价与库存");
        }
        if (request == null) {
            throw new BusinessException("活动商品配置不能为空");
        }

        CoffeeActivityProduct binding = getExistingBinding(activityId, bindingId);
        CoffeeProduct product = productMapper.selectById(binding.getProductId());
        if (product == null) {
            throw new BusinessException("商品不存在");
        }
        if (product.getStatus() == null || product.getStatus() != PRODUCT_ON_SHELF) {
            throw new BusinessException("商品未上架，不能修改活动配置");
        }
        int soldCount = binding.getSoldCount() == null ? 0 : binding.getSoldCount();
        validateSeckillBinding(request.activityPrice(), request.activityStock(),
                request.limitPerUser(), product, soldCount);

        binding.setActivityPrice(request.activityPrice());
        binding.setActivityStock(request.activityStock());
        binding.setLimitPerUser(request.limitPerUser());
        activityProductMapper.updateById(binding);
        seckillStockRepository.clearStock(bindingId);
        log.info("活动商品配置更新成功 activityId={}, bindingId={}", activityId, bindingId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disableActivityProduct(Long activityId, Long bindingId) {
        CoffeeActivity activity = getExistingActivity(activityId);
        requireClosed(activity);
        CoffeeActivityProduct binding = getExistingBinding(activityId, bindingId);
        if (binding.getStatus() != null && binding.getStatus() == BINDING_DISABLED) {
            return;
        }

        CoffeeActivityProduct updateBinding = new CoffeeActivityProduct();
        updateBinding.setId(bindingId);
        updateBinding.setStatus(BINDING_DISABLED);
        activityProductMapper.updateById(updateBinding);
        clearSeckillStock(activity, bindingId);
        log.info("活动商品绑定已停用 activityId={}, bindingId={}", activityId, bindingId);
    }

    @Override
    public AdminActivityDetailVO preheat(Long activityId) {
        CoffeeActivity activity = getExistingActivity(activityId);
        if (!ActivityType.SECKILL.name().equals(activity.getType())) {
            throw new BusinessException("仅秒杀活动支持库存预热");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!activity.getEndTime().isAfter(now)) {
            throw new BusinessException("活动已结束，无法预热");
        }

        List<CoffeeActivityProduct> bindings = listActiveBindings(activityId);
        if (bindings.isEmpty()) {
            throw new BusinessException("请先绑定活动商品");
        }
        Duration ttl = preheatTtl(activity.getEndTime(), now);
        for (CoffeeActivityProduct binding : bindings) {
            int stock = binding.getActivityStock() == null ? 0 : binding.getActivityStock();
            int sold = binding.getSoldCount() == null ? 0 : binding.getSoldCount();
            int targetStock = Math.max(stock - sold, 0);
            Long remaining = seckillStockRepository.preheat(binding.getId(), targetStock, ttl);
            log.info("秒杀库存预热 activityId={}, bindingId={}, target={}, remaining={}",
                    activityId, binding.getId(), targetStock, remaining);
        }
        return getActivityDetail(activityId);
    }

    @Override
    public AdminActivityDetailVO getPreheatStatus(Long activityId) {
        return getActivityDetail(activityId);
    }

    @Override
    public Set<Long> listActiveSeckillProductIds() {
        LocalDateTime now = LocalDateTime.now();
        List<CoffeeActivity> activities = activityMapper.selectList(
                Wrappers.<CoffeeActivity>lambdaQuery()
                        .eq(CoffeeActivity::getType, ActivityType.SECKILL.name())
                        .eq(CoffeeActivity::getIsOpen, ACTIVITY_OPEN)
                        .le(CoffeeActivity::getStartTime, now)
                        .ge(CoffeeActivity::getEndTime, now));
        if (activities.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> activityIds = activities.stream().map(CoffeeActivity::getId).toList();
        return activityProductMapper.selectList(Wrappers.<CoffeeActivityProduct>lambdaQuery()
                        .in(CoffeeActivityProduct::getActivityId, activityIds)
                        .eq(CoffeeActivityProduct::getStatus, BINDING_ENABLED))
                .stream()
                .map(CoffeeActivityProduct::getProductId)
                .collect(Collectors.toSet());
    }

    @Override
    public FullReductionResult calculateFullReduction(Map<Long, Long> productSubtotals) {
        if (productSubtotals == null || productSubtotals.isEmpty()) {
            return FullReductionResult.none();
        }
        LocalDateTime now = LocalDateTime.now();
        List<CoffeeActivity> activities = activityMapper.selectList(
                Wrappers.<CoffeeActivity>lambdaQuery()
                        .eq(CoffeeActivity::getType, ActivityType.FULL_REDUCTION.name())
                        .eq(CoffeeActivity::getIsOpen, ACTIVITY_OPEN)
                        .le(CoffeeActivity::getStartTime, now)
                        .ge(CoffeeActivity::getEndTime, now)
                        .orderByAsc(CoffeeActivity::getEndTime)
                        .orderByAsc(CoffeeActivity::getId));
        if (activities.isEmpty()) {
            return FullReductionResult.none();
        }

        List<Long> activityIds = activities.stream().map(CoffeeActivity::getId).toList();
        Map<Long, Set<Long>> boundProductIds = activityProductMapper.selectList(
                        Wrappers.<CoffeeActivityProduct>lambdaQuery()
                                .in(CoffeeActivityProduct::getActivityId, activityIds)
                                .eq(CoffeeActivityProduct::getStatus, BINDING_ENABLED))
                .stream()
                .collect(Collectors.groupingBy(
                        CoffeeActivityProduct::getActivityId,
                        Collectors.mapping(CoffeeActivityProduct::getProductId, Collectors.toSet())));

        long allAmount = productSubtotals.values().stream().mapToLong(Long::longValue).sum();
        FullReductionResult best = FullReductionResult.none();
        for (CoffeeActivity activity : activities) {
            long fullAmount = activity.getFullAmount() == null ? 0L : activity.getFullAmount();
            long reduceAmount = activity.getReduceAmount() == null ? 0L : activity.getReduceAmount();
            if (fullAmount <= 0 || reduceAmount <= 0) {
                continue;
            }
            Set<Long> boundIds = boundProductIds.get(activity.getId());
            long eligibleAmount;
            if (boundIds == null || boundIds.isEmpty()) {
                eligibleAmount = allAmount;
            } else {
                eligibleAmount = productSubtotals.entrySet().stream()
                        .filter(entry -> boundIds.contains(entry.getKey()))
                        .mapToLong(Map.Entry::getValue)
                        .sum();
            }
            if (eligibleAmount < fullAmount) {
                continue;
            }
            long discountAmount = Math.min(reduceAmount, eligibleAmount);
            if (discountAmount > best.discountAmount()) {
                best = new FullReductionResult(activity.getId(), discountAmount);
            }
        }
        return best;
    }

    @Override
    public List<SeckillActivityVO> listRunningSeckillActivities() {
        LocalDateTime now = LocalDateTime.now();
        List<CoffeeActivity> activities = activityMapper.selectList(
                Wrappers.<CoffeeActivity>lambdaQuery()
                        .eq(CoffeeActivity::getType, ActivityType.SECKILL.name())
                        .eq(CoffeeActivity::getIsOpen, ACTIVITY_OPEN)
                        .le(CoffeeActivity::getStartTime, now)
                        .ge(CoffeeActivity::getEndTime, now)
                        .orderByAsc(CoffeeActivity::getStartTime)
                        .orderByAsc(CoffeeActivity::getId));
        if (activities.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> activityIds = activities.stream().map(CoffeeActivity::getId).toList();
        List<CoffeeActivityProduct> bindings = activityProductMapper.selectList(
                Wrappers.<CoffeeActivityProduct>lambdaQuery()
                        .in(CoffeeActivityProduct::getActivityId, activityIds)
                        .eq(CoffeeActivityProduct::getStatus, BINDING_ENABLED)
                        .orderByAsc(CoffeeActivityProduct::getSort)
                        .orderByAsc(CoffeeActivityProduct::getId));
        if (bindings.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> productIds = bindings.stream()
                .map(CoffeeActivityProduct::getProductId)
                .collect(Collectors.toSet());
        Map<Long, CoffeeProduct> productMap = productMapper.selectByIds(productIds).stream()
                .filter(product -> product.getStatus() != null && product.getStatus() == PRODUCT_ON_SHELF)
                .collect(Collectors.toMap(CoffeeProduct::getId, Function.identity()));
        if (productMap.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, List<MenuSpecGroupVO>> specGroupsByProduct =
                loadSpecGroupsByProduct(productMap.keySet());
        Map<Long, List<CoffeeActivityProduct>> bindingsByActivity = bindings.stream()
                .collect(Collectors.groupingBy(CoffeeActivityProduct::getActivityId));

        List<SeckillActivityVO> result = new java.util.ArrayList<>(activities.size());
        for (CoffeeActivity activity : activities) {
            List<SeckillProductVO> products = bindingsByActivity
                    .getOrDefault(activity.getId(), Collections.emptyList())
                    .stream()
                    .map(binding -> toSeckillProductVO(activity, binding,
                            productMap.get(binding.getProductId()),
                            specGroupsByProduct.getOrDefault(binding.getProductId(), Collections.emptyList())))
                    .filter(Objects::nonNull)
                    .toList();
            if (products.isEmpty()) {
                continue;
            }
            result.add(new SeckillActivityVO(
                    activity.getId(),
                    activity.getTitle(),
                    activity.getStartTime(),
                    activity.getEndTime(),
                    products));
        }
        return result;
    }

    private SeckillProductVO toSeckillProductVO(CoffeeActivity activity,
                                                CoffeeActivityProduct binding,
                                                CoffeeProduct product,
                                                List<MenuSpecGroupVO> specGroups) {
        if (product == null) {
            return null;
        }
        Integer redisStock = null;
        try {
            redisStock = seckillStockRepository.getStock(binding.getId());
        } catch (Exception exception) {
            log.warn("读取秒杀库存失败，回退 DB 剩余量 bindingId={}", binding.getId(), exception);
        }
        int stock = binding.getActivityStock() == null ? 0 : binding.getActivityStock();
        int sold = binding.getSoldCount() == null ? 0 : binding.getSoldCount();
        int remainingStock = redisStock != null ? redisStock : Math.max(stock - sold, 0);
        return new SeckillProductVO(
                binding.getId(),
                activity.getId(),
                product.getId(),
                product.getName(),
                product.getPicUrl(),
                product.getPrice(),
                binding.getActivityPrice(),
                stock,
                sold,
                remainingStock,
                binding.getLimitPerUser(),
                redisStock != null,
                specGroups);
    }

    private Map<Long, List<MenuSpecGroupVO>> loadSpecGroupsByProduct(Collection<Long> productIds) {
        List<CoffeeProductSpecGroup> productGroups = productSpecGroupMapper.selectList(
                Wrappers.<CoffeeProductSpecGroup>lambdaQuery()
                        .in(CoffeeProductSpecGroup::getProductId, productIds)
                        .orderByAsc(CoffeeProductSpecGroup::getSort)
                        .orderByAsc(CoffeeProductSpecGroup::getId));
        if (productGroups.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, List<Long>> groupIdsByProduct = productGroups.stream()
                .collect(Collectors.groupingBy(
                        CoffeeProductSpecGroup::getProductId,
                        Collectors.mapping(CoffeeProductSpecGroup::getGroupId, Collectors.toList())));
        Set<Long> groupIds = productGroups.stream()
                .map(CoffeeProductSpecGroup::getGroupId)
                .collect(Collectors.toSet());
        Map<Long, CoffeeSpecGroup> groupMap = specGroupMapper.selectByIds(groupIds).stream()
                .collect(Collectors.toMap(CoffeeSpecGroup::getId, Function.identity()));
        Map<Long, List<CoffeeSpecOption>> optionsByGroup = specOptionMapper.selectList(
                        Wrappers.<CoffeeSpecOption>lambdaQuery()
                                .in(CoffeeSpecOption::getGroupId, groupIds)
                                .eq(CoffeeSpecOption::getStatus, 1)
                                .orderByAsc(CoffeeSpecOption::getSort)
                                .orderByAsc(CoffeeSpecOption::getId))
                .stream()
                .collect(Collectors.groupingBy(CoffeeSpecOption::getGroupId));

        Map<Long, List<MenuSpecGroupVO>> result = new java.util.HashMap<>();
        for (Long productId : productIds) {
            List<MenuSpecGroupVO> groups = groupIdsByProduct
                    .getOrDefault(productId, Collections.emptyList())
                    .stream()
                    .map(groupMap::get)
                    .filter(Objects::nonNull)
                    .map(group -> {
                        List<MenuSpecOptionVO> options = optionsByGroup
                                .getOrDefault(group.getId(), Collections.emptyList())
                                .stream()
                                .map(option -> new MenuSpecOptionVO(
                                        option.getId(), option.getName(), option.getExtraPrice()))
                                .toList();
                        if (options.isEmpty()) {
                            return null;
                        }
                        return new MenuSpecGroupVO(
                                group.getId(),
                                group.getName(),
                                group.getRequired() != null && group.getRequired() == 1,
                                group.getMultiple() != null && group.getMultiple() == 1,
                                options);
                    })
                    .filter(Objects::nonNull)
                    .toList();
            result.put(productId, groups);
        }
        return result;
    }

    private AdminActivityVO toActivityVO(CoffeeActivity activity,
                                         List<CoffeeActivityProduct> bindings,
                                         LocalDateTime now) {
        List<CoffeeActivityProduct> activeBindings = bindings.stream()
                .filter(binding -> binding.getStatus() != null && binding.getStatus() == BINDING_ENABLED)
                .toList();
        int productCount = activeBindings.size();
        int totalStock = activeBindings.stream()
                .mapToInt(binding -> binding.getActivityStock() == null ? 0 : binding.getActivityStock())
                .sum();
        int soldCount = bindings.stream()
                .mapToInt(binding -> binding.getSoldCount() == null ? 0 : binding.getSoldCount())
                .sum();
        String status = ActivityStatus.resolve(activity.getIsOpen(), activity.getStartTime(),
                activity.getEndTime(), now).name();
        return new AdminActivityVO(
                activity.getId(),
                activity.getTitle(),
                activity.getType(),
                activity.getStartTime(),
                activity.getEndTime(),
                activity.getIsOpen(),
                status,
                activity.getFullAmount(),
                activity.getReduceAmount(),
                activity.getRemark(),
                productCount,
                totalStock,
                soldCount,
                ActivityType.SECKILL.name().equals(activity.getType()),
                activity.getCreateTime());
    }

    private AdminActivityProductVO toProductVO(CoffeeActivity activity,
                                               CoffeeActivityProduct binding,
                                               CoffeeProduct product) {
        Integer redisStock = null;
        boolean preheated = false;
        boolean seckill = ActivityType.SECKILL.name().equals(activity.getType());
        if (seckill && binding.getStatus() != null && binding.getStatus() == BINDING_ENABLED) {
            try {
                redisStock = seckillStockRepository.getStock(binding.getId());
                preheated = redisStock != null;
            } catch (Exception exception) {
                log.warn("读取秒杀预热状态失败 bindingId={}", binding.getId(), exception);
            }
        }
        return new AdminActivityProductVO(
                binding.getId(),
                binding.getProductId(),
                product == null ? "商品已删除" : product.getName(),
                product == null ? "" : product.getPicUrl(),
                product == null || product.getPrice() == null ? 0L : product.getPrice(),
                product == null ? 0 : product.getStatus(),
                binding.getActivityPrice(),
                binding.getActivityStock(),
                binding.getSoldCount(),
                binding.getLimitPerUser(),
                binding.getStatus(),
                binding.getSort(),
                redisStock,
                preheated);
    }

    private boolean resolveAllPreheated(CoffeeActivity activity,
                                        List<AdminActivityProductVO> products) {
        if (!ActivityType.SECKILL.name().equals(activity.getType())) {
            return true;
        }
        List<AdminActivityProductVO> activeProducts = products.stream()
                .filter(product -> product.status() != null && product.status() == BINDING_ENABLED)
                .toList();
        return !activeProducts.isEmpty() && activeProducts.stream().allMatch(AdminActivityProductVO::preheated);
    }

    private void validateSaveRequest(SaveActivityRequest request) {
        if (request == null) {
            throw new BusinessException("活动信息不能为空");
        }
        if (StrUtil.isBlank(request.title())) {
            throw new BusinessException("活动名称不能为空");
        }
        if (request.title().trim().length() > MAX_TITLE_LENGTH) {
            throw new BusinessException("活动名称不能超过 " + MAX_TITLE_LENGTH + " 个字符");
        }
        if (!ActivityType.isValid(request.type())) {
            throw new BusinessException("活动类型不合法");
        }
        if (request.startTime() == null || request.endTime() == null) {
            throw new BusinessException("请选择活动起止时间");
        }
        if (!request.startTime().isBefore(request.endTime())) {
            throw new BusinessException("开始时间必须早于结束时间");
        }
        if (request.remark() != null && request.remark().length() > MAX_REMARK_LENGTH) {
            throw new BusinessException("备注不能超过 " + MAX_REMARK_LENGTH + " 个字符");
        }
        if (ActivityType.FULL_REDUCTION.name().equals(request.type())) {
            if (request.fullAmount() == null || request.fullAmount() <= 0) {
                throw new BusinessException("满减门槛必须大于 0");
            }
            if (request.reduceAmount() == null || request.reduceAmount() <= 0) {
                throw new BusinessException("满减金额必须大于 0");
            }
            if (request.reduceAmount() > request.fullAmount()) {
                throw new BusinessException("满减金额不能大于门槛金额");
            }
        }
    }

    private void applySaveRequest(CoffeeActivity activity, SaveActivityRequest request) {
        activity.setTitle(request.title().trim());
        activity.setType(request.type());
        activity.setStartTime(request.startTime());
        activity.setEndTime(request.endTime());
        activity.setRemark(StrUtil.blankToDefault(request.remark(), ""));
        if (ActivityType.SECKILL.name().equals(request.type())) {
            activity.setFullAmount(0L);
            activity.setReduceAmount(0L);
        } else {
            activity.setFullAmount(request.fullAmount());
            activity.setReduceAmount(request.reduceAmount());
        }
    }

    private void validateSeckillOpen(CoffeeActivity activity) {
        List<CoffeeActivityProduct> bindings = listActiveBindings(activity.getId());
        if (bindings.isEmpty()) {
            throw new BusinessException("请先绑定活动商品");
        }
        for (CoffeeActivityProduct binding : bindings) {
            CoffeeProduct product = productMapper.selectById(binding.getProductId());
            if (product == null) {
                throw new BusinessException("活动商品已删除，请先停用该绑定");
            }
            if (product.getStatus() == null || product.getStatus() != PRODUCT_ON_SHELF) {
                throw new BusinessException("商品「" + product.getName() + "」已下架，不能开启活动");
            }
            int soldCount = binding.getSoldCount() == null ? 0 : binding.getSoldCount();
            validateSeckillBinding(binding.getActivityPrice(), binding.getActivityStock(),
                    binding.getLimitPerUser(), product, soldCount);
            if (seckillStockRepository.getStock(binding.getId()) == null) {
                throw new BusinessException("商品「" + product.getName() + "」未预热库存，请先一键预热");
            }
        }
    }

    private void validateFullReduction(CoffeeActivity activity) {
        if (activity.getFullAmount() == null || activity.getFullAmount() <= 0) {
            throw new BusinessException("满减门槛必须大于 0");
        }
        if (activity.getReduceAmount() == null || activity.getReduceAmount() <= 0) {
            throw new BusinessException("满减金额必须大于 0");
        }
        if (activity.getReduceAmount() > activity.getFullAmount()) {
            throw new BusinessException("满减金额不能大于门槛金额");
        }
    }

    private void validateSeckillBinding(Long activityPrice, Integer activityStock,
                                        Integer limitPerUser, CoffeeProduct product, int soldCount) {
        if (activityPrice == null || activityPrice <= 0) {
            throw new BusinessException("秒杀价必须大于 0");
        }
        if (product.getPrice() != null && activityPrice >= product.getPrice()) {
            throw new BusinessException("秒杀价必须低于商品原价");
        }
        if (activityStock == null || activityStock <= 0) {
            throw new BusinessException("活动限量必须大于 0");
        }
        int productStock = product.getStock() == null ? 0 : product.getStock();
        if (activityStock > productStock) {
            throw new BusinessException("活动限量不能超过商品当前库存（" + productStock + " 杯）");
        }
        if (activityStock < soldCount) {
            throw new BusinessException("活动限量不能小于已售数量（" + soldCount + " 杯）");
        }
        if (limitPerUser == null || limitPerUser <= 0) {
            throw new BusinessException("每人限购必须大于 0");
        }
        if (limitPerUser > activityStock) {
            throw new BusinessException("每人限购不能大于活动限量");
        }
    }

    private void requireClosed(CoffeeActivity activity) {
        if (activity.getIsOpen() != null && activity.getIsOpen() == ACTIVITY_OPEN) {
            throw new BusinessException("活动开启中，请先关闭活动再修改商品与价格");
        }
    }

    private CoffeeActivity getExistingActivity(Long activityId) {
        if (activityId == null) {
            throw new BusinessException("活动 ID 不能为空");
        }
        CoffeeActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException("活动不存在");
        }
        return activity;
    }

    private CoffeeActivityProduct getExistingBinding(Long activityId, Long bindingId) {
        if (bindingId == null) {
            throw new BusinessException("活动商品 ID 不能为空");
        }
        CoffeeActivityProduct binding = activityProductMapper.selectOne(
                Wrappers.<CoffeeActivityProduct>lambdaQuery()
                        .eq(CoffeeActivityProduct::getId, bindingId)
                        .eq(CoffeeActivityProduct::getActivityId, activityId));
        if (binding == null) {
            throw new BusinessException("活动商品不存在");
        }
        return binding;
    }

    private List<CoffeeActivityProduct> listActiveBindings(Long activityId) {
        return activityProductMapper.selectList(Wrappers.<CoffeeActivityProduct>lambdaQuery()
                .eq(CoffeeActivityProduct::getActivityId, activityId)
                .eq(CoffeeActivityProduct::getStatus, BINDING_ENABLED)
                .orderByAsc(CoffeeActivityProduct::getSort)
                .orderByAsc(CoffeeActivityProduct::getId));
    }

    private Map<Long, List<CoffeeActivityProduct>> loadBindings(Collection<Long> activityIds) {
        if (activityIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return activityProductMapper.selectList(Wrappers.<CoffeeActivityProduct>lambdaQuery()
                        .in(CoffeeActivityProduct::getActivityId, activityIds))
                .stream()
                .collect(Collectors.groupingBy(CoffeeActivityProduct::getActivityId));
    }

    private Map<Long, CoffeeProduct> loadProducts(Collection<Long> productIds) {
        Set<Long> ids = productIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return productMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(CoffeeProduct::getId, Function.identity()));
    }

    private int nextBindingSort(Long activityId) {
        long count = activityProductMapper.selectCount(Wrappers.<CoffeeActivityProduct>lambdaQuery()
                .eq(CoffeeActivityProduct::getActivityId, activityId)
                .eq(CoffeeActivityProduct::getStatus, BINDING_ENABLED));
        return (int) count + 1;
    }

    private void clearSeckillStock(CoffeeActivity activity, Long bindingId) {
        if (ActivityType.SECKILL.name().equals(activity.getType())) {
            seckillStockRepository.clearStock(bindingId);
        }
    }

    private Duration preheatTtl(LocalDateTime endTime, LocalDateTime now) {
        Duration ttl = Duration.between(now, endTime.plusDays(1));
        return ttl.compareTo(Duration.ofMinutes(MIN_PREHEAT_TTL_MINUTES)) < 0
                ? Duration.ofMinutes(MIN_PREHEAT_TTL_MINUTES)
                : ttl;
    }
}

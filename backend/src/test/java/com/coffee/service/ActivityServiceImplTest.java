package com.coffee.service;

import com.coffee.common.BusinessException;
import com.coffee.dto.admin.BindActivityProductRequest;
import com.coffee.dto.admin.SaveActivityRequest;
import com.coffee.entity.CoffeeActivity;
import com.coffee.entity.CoffeeActivityProduct;
import com.coffee.entity.CoffeeProduct;
import com.coffee.mapper.CoffeeActivityMapper;
import com.coffee.mapper.CoffeeActivityProductMapper;
import com.coffee.mapper.CoffeeOrderMapper;
import com.coffee.mapper.CoffeeProductMapper;
import com.coffee.service.impl.ActivityServiceImpl;
import com.coffee.vo.admin.AdminActivityDetailVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 活动后台管理单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ActivityServiceImplTest {

    @Mock
    private CoffeeActivityMapper activityMapper;
    @Mock
    private CoffeeActivityProductMapper activityProductMapper;
    @Mock
    private CoffeeProductMapper productMapper;
    @Mock
    private com.coffee.mapper.CoffeeProductSpecGroupMapper productSpecGroupMapper;
    @Mock
    private com.coffee.mapper.CoffeeSpecGroupMapper specGroupMapper;
    @Mock
    private com.coffee.mapper.CoffeeSpecOptionMapper specOptionMapper;
    @Mock
    private CoffeeOrderMapper orderMapper;
    @Mock
    private SeckillStockRepository seckillStockRepository;

    private ActivityServiceImpl activityService;

    @BeforeEach
    void setUp() {
        activityService = new ActivityServiceImpl(
                activityMapper, activityProductMapper, productMapper,
                productSpecGroupMapper, specGroupMapper, specOptionMapper,
                orderMapper, seckillStockRepository);
    }

    @Test
    @DisplayName("开始时间必须早于结束时间")
    void createActivityRejectsInvalidTimeRange() {
        LocalDateTime now = LocalDateTime.now();
        SaveActivityRequest request = new SaveActivityRequest(
                "测试活动", "SECKILL", now.plusHours(1), now, null, null, "");

        assertThatThrownBy(() -> activityService.createActivity(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("开始时间必须早于结束时间");
        verify(activityMapper, never()).insert(any(CoffeeActivity.class));
    }

    @Test
    @DisplayName("满减金额不能大于门槛")
    void createActivityRejectsInvalidFullReduction() {
        LocalDateTime now = LocalDateTime.now();
        SaveActivityRequest request = new SaveActivityRequest(
                "满减活动", "FULL_REDUCTION", now, now.plusHours(2), 3000L, 5000L, "");

        assertThatThrownBy(() -> activityService.createActivity(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("满减金额不能大于门槛金额");
    }

    @Test
    @DisplayName("活动开启中不允许绑定商品")
    void bindProductRejectsOpenActivity() {
        CoffeeActivity activity = seckillActivity(1L);
        activity.setIsOpen(1);
        when(activityMapper.selectById(1L)).thenReturn(activity);

        assertThatThrownBy(() -> activityService.bindProduct(1L,
                new BindActivityProductRequest(5L, 1500L, 50, 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先关闭活动");
        verify(activityProductMapper, never()).insert(any(CoffeeActivityProduct.class));
    }

    @Test
    @DisplayName("满减活动不支持库存预热")
    void preheatRejectsFullReductionActivity() {
        CoffeeActivity activity = seckillActivity(1L);
        activity.setType("FULL_REDUCTION");
        when(activityMapper.selectById(1L)).thenReturn(activity);

        assertThatThrownBy(() -> activityService.preheat(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅秒杀活动支持库存预热");
    }

    @Test
    @DisplayName("预热库存 = 活动限量 - 已售")
    void preheatUsesRemainingStock() {
        CoffeeActivity activity = seckillActivity(1L);
        activity.setEndTime(LocalDateTime.now().plusDays(1));
        CoffeeActivityProduct binding = binding(10L, 5L, 100, 40);
        CoffeeProduct product = product(5L);

        when(activityMapper.selectById(1L)).thenReturn(activity);
        when(activityProductMapper.selectList(any())).thenReturn(List.of(binding));
        when(productMapper.selectByIds(any())).thenReturn(List.of(product));
        when(seckillStockRepository.preheat(eq(10L), eq(60), any())).thenReturn(60L);
        when(seckillStockRepository.getStock(10L)).thenReturn(60);

        AdminActivityDetailVO detail = activityService.preheat(1L);

        verify(seckillStockRepository).preheat(eq(10L), eq(60), any());
        assertThat(detail.allPreheated()).isTrue();
        assertThat(detail.products()).hasSize(1);
        assertThat(detail.products().get(0).redisStock()).isEqualTo(60);
    }

    @Test
    @DisplayName("秒杀未预热时不能开启")
    void openSeckillRequiresPreheat() {
        CoffeeActivity activity = seckillActivity(1L);
        activity.setEndTime(LocalDateTime.now().plusDays(1));
        CoffeeActivityProduct binding = binding(10L, 5L, 100, 0);
        CoffeeProduct product = product(5L);

        when(activityMapper.selectById(1L)).thenReturn(activity);
        when(activityProductMapper.selectList(any())).thenReturn(List.of(binding));
        when(productMapper.selectById(5L)).thenReturn(product);
        when(seckillStockRepository.getStock(10L)).thenReturn(null);

        assertThatThrownBy(() -> activityService.updateOpen(1L, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未预热库存");
        verify(activityMapper, never()).updateById(any(CoffeeActivity.class));
    }

    @Test
    @DisplayName("已预热后可以开启秒杀")
    void openSeckillAfterPreheat() {
        CoffeeActivity activity = seckillActivity(1L);
        activity.setEndTime(LocalDateTime.now().plusDays(1));
        CoffeeActivityProduct binding = binding(10L, 5L, 100, 0);
        CoffeeProduct product = product(5L);

        when(activityMapper.selectById(1L)).thenReturn(activity);
        when(activityProductMapper.selectList(any())).thenReturn(List.of(binding));
        when(productMapper.selectById(5L)).thenReturn(product);
        when(seckillStockRepository.getStock(10L)).thenReturn(100);

        activityService.updateOpen(1L, true);

        ArgumentCaptor<CoffeeActivity> captor = ArgumentCaptor.forClass(CoffeeActivity.class);
        verify(activityMapper).updateById(captor.capture());
        assertThat(captor.getValue().getIsOpen()).isEqualTo(1);
    }

    @Test
    @DisplayName("停用绑定：status=0 并清理 Redis 库存")
    void disableBindingClearsStock() {
        CoffeeActivity activity = seckillActivity(1L);
        CoffeeActivityProduct binding = binding(10L, 5L, 100, 0);
        when(activityMapper.selectById(1L)).thenReturn(activity);
        when(activityProductMapper.selectOne(any())).thenReturn(binding);

        activityService.disableActivityProduct(1L, 10L);

        ArgumentCaptor<CoffeeActivityProduct> captor =
                ArgumentCaptor.forClass(CoffeeActivityProduct.class);
        verify(activityProductMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isZero();
        verify(seckillStockRepository).clearStock(10L);
    }

    private CoffeeActivity seckillActivity(Long id) {
        CoffeeActivity activity = new CoffeeActivity();
        activity.setId(id);
        activity.setTitle("测试秒杀");
        activity.setType("SECKILL");
        activity.setStartTime(LocalDateTime.now().minusMinutes(10));
        activity.setEndTime(LocalDateTime.now().plusHours(2));
        activity.setIsOpen(0);
        activity.setFullAmount(0L);
        activity.setReduceAmount(0L);
        activity.setRemark("");
        return activity;
    }

    private CoffeeActivityProduct binding(Long id, Long productId, int stock, int soldCount) {
        CoffeeActivityProduct binding = new CoffeeActivityProduct();
        binding.setId(id);
        binding.setActivityId(1L);
        binding.setProductId(productId);
        binding.setActivityPrice(1500L);
        binding.setActivityStock(stock);
        binding.setSoldCount(soldCount);
        binding.setLimitPerUser(2);
        binding.setStatus(1);
        binding.setSort(1);
        return binding;
    }

    private CoffeeProduct product(Long id) {
        CoffeeProduct product = new CoffeeProduct();
        product.setId(id);
        product.setCategoryId(1L);
        product.setName("生椰拿铁");
        product.setPicUrl("");
        product.setPrice(2600L);
        product.setStock(100);
        product.setStatus(1);
        return product;
    }
}

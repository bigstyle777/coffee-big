package com.coffee.service;

import com.coffee.common.BusinessException;
import com.coffee.dto.order.CreateSeckillOrderRequest;
import com.coffee.dto.order.SeckillOrderContext;
import com.coffee.entity.CoffeeActivity;
import com.coffee.entity.CoffeeActivityProduct;
import com.coffee.entity.CoffeeOrder;
import com.coffee.entity.CoffeeProduct;
import com.coffee.mapper.CoffeeActivityMapper;
import com.coffee.mapper.CoffeeActivityProductMapper;
import com.coffee.mapper.CoffeeOrderMapper;
import com.coffee.mapper.CoffeeProductMapper;
import com.coffee.mapper.CoffeeProductSpecGroupMapper;
import com.coffee.mapper.CoffeeSpecGroupMapper;
import com.coffee.mapper.CoffeeSpecOptionMapper;
import com.coffee.service.impl.SeckillOrderServiceImpl;
import com.coffee.service.impl.SeckillOrderTxService;
import com.coffee.vo.order.OrderCreateVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 秒杀下单编排单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SeckillOrderServiceImplTest {

    @Mock
    private CoffeeActivityProductMapper activityProductMapper;
    @Mock
    private CoffeeActivityMapper activityMapper;
    @Mock
    private CoffeeProductMapper productMapper;
    @Mock
    private CoffeeProductSpecGroupMapper productSpecGroupMapper;
    @Mock
    private CoffeeSpecGroupMapper specGroupMapper;
    @Mock
    private CoffeeSpecOptionMapper specOptionMapper;
    @Mock
    private CoffeeOrderMapper orderMapper;
    @Mock
    private SeckillStockRepository seckillStockRepository;
    @Mock
    private SeckillOrderTxService seckillOrderTxService;

    private SeckillOrderServiceImpl seckillOrderService;

    @BeforeEach
    void setUp() {
        seckillOrderService = new SeckillOrderServiceImpl(
                activityProductMapper, activityMapper, productMapper,
                productSpecGroupMapper, specGroupMapper, specOptionMapper,
                orderMapper, seckillStockRepository, seckillOrderTxService,
                JsonMapper.builder().build());

        when(activityProductMapper.selectById(10L)).thenReturn(binding());
        when(activityMapper.selectById(1L)).thenReturn(activity());
        when(productMapper.selectById(5L)).thenReturn(product());
        when(productSpecGroupMapper.selectList(any())).thenReturn(List.of());
        when(seckillStockRepository.acquireRequestLock(eq(9L), eq("req-1"), any()))
                .thenReturn(true);
    }

    @Test
    @DisplayName("秒杀下单成功：Redis 预扣后调用 DB 事务")
    void createSeckillOrderSucceeds() {
        when(seckillStockRepository.deduct(eq(10L), eq(9L), eq(1), eq(2), any()))
                .thenReturn(19L);
        when(seckillOrderTxService.createSeckillOrder(any())).thenReturn(
                new OrderCreateVO(100L, "ORDER-100", null, "SELF_PICKUP",
                        1200L, 0L, 1200L, 1, "PENDING_PAY"));

        OrderCreateVO result = seckillOrderService.createSeckillOrder(9L, request(1));

        assertThat(result.orderId()).isEqualTo(100L);
        assertThat(result.payAmount()).isEqualTo(1200L);
        ArgumentCaptor<SeckillOrderContext> captor =
                ArgumentCaptor.forClass(SeckillOrderContext.class);
        verify(seckillOrderTxService).createSeckillOrder(captor.capture());
        assertThat(captor.getValue().activityPrice()).isEqualTo(1200L);
        assertThat(captor.getValue().unitPrice()).isEqualTo(1200L);
        assertThat(captor.getValue().totalAmount()).isEqualTo(1200L);
        assertThat(captor.getValue().specExtraPrice()).isZero();
        verify(seckillStockRepository).releaseRequestLock(9L, "req-1");
    }

    @Test
    @DisplayName("未预热时拒绝下单且不调用 DB 事务")
    void createSeckillOrderRejectsNotPreheated() {
        when(seckillStockRepository.deduct(eq(10L), eq(9L), eq(1), eq(2), any()))
                .thenReturn(SeckillStockRepository.NOT_PREHEATED);

        assertThatThrownBy(() -> seckillOrderService.createSeckillOrder(9L, request(1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未就绪");
        verify(seckillOrderTxService, never()).createSeckillOrder(any());
        verify(seckillStockRepository, never()).rollback(any(), any(), eq(1), any());
    }

    @Test
    @DisplayName("已售罄时拒绝下单")
    void createSeckillOrderRejectsSoldOut() {
        when(seckillStockRepository.deduct(eq(10L), eq(9L), eq(1), eq(2), any()))
                .thenReturn(SeckillStockRepository.SOLD_OUT);

        assertThatThrownBy(() -> seckillOrderService.createSeckillOrder(9L, request(1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已抢完");
        verify(seckillOrderTxService, never()).createSeckillOrder(any());
    }

    @Test
    @DisplayName("超过每人限购时拒绝下单")
    void createSeckillOrderRejectsLimitExceeded() {
        when(seckillStockRepository.deduct(eq(10L), eq(9L), eq(1), eq(2), any()))
                .thenReturn(SeckillStockRepository.LIMIT_EXCEEDED);

        assertThatThrownBy(() -> seckillOrderService.createSeckillOrder(9L, request(1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("限购");
        verify(seckillOrderTxService, never()).createSeckillOrder(any());
    }

    @Test
    @DisplayName("DB 落库失败时回滚 Redis 预扣")
    void createSeckillOrderRollsBackOnDatabaseFailure() {
        when(seckillStockRepository.deduct(eq(10L), eq(9L), eq(1), eq(2), any()))
                .thenReturn(19L);
        when(seckillOrderTxService.createSeckillOrder(any()))
                .thenThrow(new BusinessException("活动库存不足，请稍后重试"));

        assertThatThrownBy(() -> seckillOrderService.createSeckillOrder(9L, request(1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("活动库存不足");
        verify(seckillStockRepository).rollback(eq(10L), eq(9L), eq(1), any());
        verify(seckillStockRepository).releaseRequestLock(9L, "req-1");
    }

    @Test
    @DisplayName("重复 requestId 直接返回已有订单，不再扣 Redis")
    void createSeckillOrderIsIdempotent() {
        CoffeeOrder existing = new CoffeeOrder();
        existing.setId(88L);
        existing.setOrderNo("ORDER-88");
        existing.setPickupType("SELF_PICKUP");
        existing.setTotalAmount(1200L);
        existing.setDiscountAmount(0L);
        existing.setPayAmount(1200L);
        existing.setItemCount(1);
        existing.setStatus("PENDING_PAY");
        when(orderMapper.selectOne(any())).thenReturn(existing);

        OrderCreateVO result = seckillOrderService.createSeckillOrder(9L, request(1));

        assertThat(result.orderId()).isEqualTo(88L);
        verify(seckillStockRepository, never()).acquireRequestLock(any(), any(), any());
        verify(seckillStockRepository, never()).deduct(any(), any(), eq(1), eq(2), any());
    }

    private CreateSeckillOrderRequest request(int quantity) {
        return new CreateSeckillOrderRequest(10L, quantity, List.of(), "SELF_PICKUP", "", "req-1");
    }

    private CoffeeActivityProduct binding() {
        CoffeeActivityProduct binding = new CoffeeActivityProduct();
        binding.setId(10L);
        binding.setActivityId(1L);
        binding.setProductId(5L);
        binding.setActivityPrice(1200L);
        binding.setActivityStock(20);
        binding.setSoldCount(0);
        binding.setLimitPerUser(2);
        binding.setStatus(1);
        binding.setSort(1);
        return binding;
    }

    private CoffeeActivity activity() {
        CoffeeActivity activity = new CoffeeActivity();
        activity.setId(1L);
        activity.setTitle("测试秒杀");
        activity.setType("SECKILL");
        activity.setIsOpen(1);
        activity.setStartTime(LocalDateTime.now().minusMinutes(10));
        activity.setEndTime(LocalDateTime.now().plusHours(1));
        activity.setFullAmount(0L);
        activity.setReduceAmount(0L);
        activity.setRemark("");
        return activity;
    }

    private CoffeeProduct product() {
        CoffeeProduct product = new CoffeeProduct();
        product.setId(5L);
        product.setCategoryId(1L);
        product.setName("美式咖啡");
        product.setPicUrl("");
        product.setPrice(1800L);
        product.setStock(100);
        product.setStatus(1);
        return product;
    }
}

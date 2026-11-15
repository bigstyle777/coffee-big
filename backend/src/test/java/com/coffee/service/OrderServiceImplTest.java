package com.coffee.service;

import com.coffee.common.BusinessException;
import com.coffee.dto.order.CreateOrderItemRequest;
import com.coffee.dto.order.CreateOrderRequest;
import com.coffee.entity.CoffeeOrder;
import com.coffee.entity.CoffeeOrderItem;
import com.coffee.entity.CoffeeProduct;
import com.coffee.entity.CoffeeProductSpecGroup;
import com.coffee.entity.CoffeeSpecGroup;
import com.coffee.entity.CoffeeSpecOption;
import com.coffee.mapper.CoffeeOrderItemMapper;
import com.coffee.mapper.CoffeeOrderMapper;
import com.coffee.mapper.CoffeeProductMapper;
import com.coffee.mapper.CoffeeProductSpecGroupMapper;
import com.coffee.mapper.CoffeeSpecGroupMapper;
import com.coffee.mapper.CoffeeSpecOptionMapper;
import com.coffee.service.CouponService;
import com.coffee.service.impl.OrderServiceImpl;
import com.coffee.util.PickupCodeGenerator;import com.coffee.vo.order.OrderCreateVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 下单金额重算与规格校验单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceImplTest {

    @Mock
    private CoffeeProductMapper productMapper;
    @Mock
    private CoffeeSpecGroupMapper specGroupMapper;
    @Mock
    private CoffeeSpecOptionMapper specOptionMapper;
    @Mock
    private CoffeeProductSpecGroupMapper productSpecGroupMapper;
    @Mock
    private CoffeeOrderMapper orderMapper;
    @Mock
    private CoffeeOrderItemMapper orderItemMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private PickupCodeGenerator pickupCodeGenerator;
    @Mock
    private CouponService couponService;
    @Mock
    private ActivityService activityService;
    @Mock
    private SeckillResourceService seckillResourceService;
    @Mock
    private com.coffee.mapper.CoffeeActivityMapper activityMapper;

    private OrderServiceImpl orderService;
    private CoffeeProduct product;
    private CoffeeSpecOption bigCup;
    private CoffeeSpecOption oatMilk;
    private CoffeeSpecOption mediumCup;
    private CoffeeSpecOption vanillaSyrup;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(
                productMapper, specGroupMapper, specOptionMapper, productSpecGroupMapper,
                orderMapper, orderItemMapper, eventPublisher,
                JsonMapper.builder().build(), pickupCodeGenerator, couponService,
                activityService, seckillResourceService, activityMapper);
        when(activityService.listActiveSeckillProductIds()).thenReturn(Set.of());
        when(activityService.calculateFullReduction(any()))
                .thenReturn(ActivityService.FullReductionResult.none());

        product = new CoffeeProduct();
        product.setId(1L);
        product.setName("生椰拿铁");
        product.setPicUrl("https://example.com/latte.png");
        product.setPrice(3000L);
        product.setStock(100);
        product.setStatus(1);

        // 杯型（必选、单选），加料（可选、多选）
        CoffeeSpecGroup cupGroup = specGroup(1L, "杯型", 1, 0);
        CoffeeSpecGroup extraGroup = specGroup(2L, "加料", 0, 1);
        bigCup = specOption(11L, 1L, "大杯", 300L);
        oatMilk = specOption(12L, 2L, "燕麦奶", 400L);
        mediumCup = specOption(13L, 1L, "中杯", 0L);
        vanillaSyrup = specOption(21L, 3L, "香草糖浆", 200L);

        when(productMapper.selectList(any())).thenReturn(List.of(product));
        when(productSpecGroupMapper.selectList(any())).thenReturn(List.of(
                productSpecGroup(1L, 1L), productSpecGroup(1L, 2L)));
        when(specGroupMapper.selectList(any())).thenReturn(List.of(cupGroup, extraGroup));
        when(specOptionMapper.selectList(any())).thenReturn(List.of(bigCup, oatMilk));
        when(pickupCodeGenerator.nextPickupCode()).thenReturn("A001");
        when(productMapper.deductStock(anyLong(), anyInt())).thenReturn(1);
        when(orderMapper.insert(any(CoffeeOrder.class))).thenAnswer(invocation -> {
            CoffeeOrder order = invocation.getArgument(0);
            order.setId(100L);
            return 1;
        });
        when(orderItemMapper.insert(any(CoffeeOrderItem.class))).thenReturn(1);
    }

    @Test
    @DisplayName("金额完全由数据库价格重算：基础价 + 规格加价 + 数量")
    void createOrder_recalculatesAmountFromDatabase() {
        CreateOrderRequest request = request("SELF_PICKUP",
                List.of(new CreateOrderItemRequest(1L, 2, List.of(11L, 12L))), "少冰");

        OrderCreateVO result = orderService.createOrder(9L, request);

        // 3000 + 300 + 400 = 3700/杯，2 杯共 7400 分
        assertThat(result.totalAmount()).isEqualTo(7400L);
        assertThat(result.payAmount()).isEqualTo(7400L);
        assertThat(result.itemCount()).isEqualTo(2);
        assertThat(result.pickupCode()).isNull();
        assertThat(result.status()).isEqualTo("PENDING_PAY");

        ArgumentCaptor<CoffeeOrderItem> itemCaptor = ArgumentCaptor.forClass(CoffeeOrderItem.class);
        verify(orderItemMapper).insert(itemCaptor.capture());
        CoffeeOrderItem savedItem = itemCaptor.getValue();
        assertThat(savedItem.getBasePrice()).isEqualTo(3000L);
        assertThat(savedItem.getSpecExtraPrice()).isEqualTo(700L);
        assertThat(savedItem.getUnitPrice()).isEqualTo(3700L);
        assertThat(savedItem.getSubtotalAmount()).isEqualTo(7400L);
        assertThat(savedItem.getSpecText()).isEqualTo("大杯 / 燕麦奶");
        assertThat(savedItem.getSpecJson()).contains("\"optionId\":11").contains("\"optionId\":12");

        ArgumentCaptor<CoffeeOrder> orderCaptor = ArgumentCaptor.forClass(CoffeeOrder.class);
        verify(orderMapper).insert(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getPickupType()).isEqualTo("SELF_PICKUP");
        assertThat(orderCaptor.getValue().getPickupCode()).isNull();
    }

    @Test
    @DisplayName("必选规格组未选择时拒绝下单")
    void createOrder_rejectsMissingRequiredSpec() {
        when(specOptionMapper.selectList(any())).thenReturn(List.of(oatMilk));
        CreateOrderRequest request = request("SELF_PICKUP",
                List.of(new CreateOrderItemRequest(1L, 1, List.of(12L))), "");

        assertThatThrownBy(() -> orderService.createOrder(9L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请选择「杯型」");
        verify(orderMapper, never()).insert(any(CoffeeOrder.class));
    }

    @Test
    @DisplayName("单选规格组选择多个选项时拒绝下单")
    void createOrder_rejectsMultipleOptionsInSingleSelectGroup() {
        when(specOptionMapper.selectList(any())).thenReturn(List.of(bigCup, mediumCup));
        CreateOrderRequest request = request("SELF_PICKUP",
                List.of(new CreateOrderItemRequest(1L, 1, List.of(11L, 13L))), "");

        assertThatThrownBy(() -> orderService.createOrder(9L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能选择一项");
        verify(orderMapper, never()).insert(any(CoffeeOrder.class));
    }

    @Test
    @DisplayName("商品已下架时拒绝下单")
    void createOrder_rejectsOffShelfProduct() {
        product.setStatus(0);
        CreateOrderRequest request = request("SELF_PICKUP",
                List.of(new CreateOrderItemRequest(1L, 1, List.of(11L))), "");

        assertThatThrownBy(() -> orderService.createOrder(9L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已下架");
        verify(orderMapper, never()).insert(any(CoffeeOrder.class));
    }

    @Test
    @DisplayName("规格选项不属于该商品时拒绝下单")
    void createOrder_rejectsUnboundSpecOption() {
        when(specOptionMapper.selectList(any())).thenReturn(List.of(vanillaSyrup));
        CreateOrderRequest request = request("SELF_PICKUP",
                List.of(new CreateOrderItemRequest(1L, 1, List.of(21L))), "");

        assertThatThrownBy(() -> orderService.createOrder(9L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持所选规格");
        verify(orderMapper, never()).insert(any(CoffeeOrder.class));
    }

    @Test
    @DisplayName("取餐方式非法时拒绝下单")
    void createOrder_rejectsInvalidPickupType() {
        CreateOrderRequest request = request("TAKEAWAY",
                List.of(new CreateOrderItemRequest(1L, 1, List.of(11L))), "");

        assertThatThrownBy(() -> orderService.createOrder(9L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("取餐方式不合法");
        verify(orderMapper, never()).insert(any(CoffeeOrder.class));
    }

    @Test
    @DisplayName("商品数量越界时拒绝下单")
    void createOrder_rejectsInvalidQuantity() {
        CreateOrderRequest request = request("SELF_PICKUP",
                List.of(new CreateOrderItemRequest(1L, 0, List.of(11L))), "");

        assertThatThrownBy(() -> orderService.createOrder(9L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("商品数量");
        verify(orderMapper, never()).insert(any(CoffeeOrder.class));
    }

    @Test
    @DisplayName("商品正在秒杀时拒绝普通下单")
    void createOrder_rejectsActiveSeckillProduct() {
        when(activityService.listActiveSeckillProductIds()).thenReturn(Set.of(1L));
        CreateOrderRequest request = request("SELF_PICKUP",
                List.of(new CreateOrderItemRequest(1L, 1, List.of(11L))), "");

        assertThatThrownBy(() -> orderService.createOrder(9L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("正在秒杀");
        verify(orderMapper, never()).insert(any(CoffeeOrder.class));
    }

    @Test
    @DisplayName("满减先于优惠券计算并写入订单")
    void createOrder_appliesFullReduction() {
        when(activityService.calculateFullReduction(any()))
                .thenReturn(new ActivityService.FullReductionResult(7L, 500L));
        when(specOptionMapper.selectList(any())).thenReturn(List.of(bigCup));
        CreateOrderRequest request = request("SELF_PICKUP",
                List.of(new CreateOrderItemRequest(1L, 1, List.of(11L))), "");

        OrderCreateVO result = orderService.createOrder(9L, request);

        // 3000 + 300 = 3300，满减 500，实付 2800
        assertThat(result.totalAmount()).isEqualTo(3300L);
        assertThat(result.discountAmount()).isEqualTo(500L);
        assertThat(result.payAmount()).isEqualTo(2800L);

        ArgumentCaptor<CoffeeOrder> orderCaptor = ArgumentCaptor.forClass(CoffeeOrder.class);
        verify(orderMapper).insert(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getActivityId()).isEqualTo(7L);
        assertThat(orderCaptor.getValue().getActivityDiscountAmount()).isEqualTo(500L);
        assertThat(orderCaptor.getValue().getOrderType()).isEqualTo("NORMAL");
    }

    private CreateOrderRequest request(String pickupType, List<CreateOrderItemRequest> items, String remark) {
        return new CreateOrderRequest(items, pickupType, remark, null, null);
    }

    private CoffeeSpecGroup specGroup(Long id, String name, int required, int multiple) {
        CoffeeSpecGroup group = new CoffeeSpecGroup();
        group.setId(id);
        group.setName(name);
        group.setRequired(required);
        group.setMultiple(multiple);
        return group;
    }

    private CoffeeSpecOption specOption(Long id, Long groupId, String name, Long extraPrice) {
        CoffeeSpecOption option = new CoffeeSpecOption();
        option.setId(id);
        option.setGroupId(groupId);
        option.setName(name);
        option.setExtraPrice(extraPrice);
        option.setStatus(1);
        return option;
    }

    private CoffeeProductSpecGroup productSpecGroup(Long productId, Long groupId) {
        CoffeeProductSpecGroup binding = new CoffeeProductSpecGroup();
        binding.setProductId(productId);
        binding.setGroupId(groupId);
        return binding;
    }
}

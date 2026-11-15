package com.coffee.service;

import com.coffee.common.BusinessException;
import com.coffee.dto.admin.SaveProductRequest;
import com.coffee.entity.CoffeeCategory;
import com.coffee.entity.CoffeeProduct;
import com.coffee.entity.CoffeeProductSpecGroup;
import com.coffee.mapper.CoffeeCategoryMapper;
import com.coffee.mapper.CoffeeProductMapper;
import com.coffee.mapper.CoffeeProductSpecGroupMapper;
import com.coffee.mapper.CoffeeSpecGroupMapper;
import com.coffee.mapper.CoffeeSpecOptionMapper;
import com.coffee.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商品后台管理单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductServiceImplTest {

    @Mock
    private CoffeeProductMapper productMapper;
    @Mock
    private CoffeeCategoryMapper categoryMapper;
    @Mock
    private CoffeeProductSpecGroupMapper productSpecGroupMapper;
    @Mock
    private CoffeeSpecGroupMapper specGroupMapper;
    @Mock
    private CoffeeSpecOptionMapper specOptionMapper;

    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        productService = new ProductServiceImpl(
                productMapper, categoryMapper, productSpecGroupMapper,
                specGroupMapper, specOptionMapper);
    }

    @Test
    @DisplayName("新增商品：写入商品并按顺序绑定规格组")
    void createProductBindsSpecGroups() {
        CoffeeCategory category = new CoffeeCategory();
        category.setId(1L);
        when(categoryMapper.selectById(1L)).thenReturn(category);
        when(specGroupMapper.selectCount(any())).thenReturn(2L);
        doAnswer(invocation -> {
            CoffeeProduct product = invocation.getArgument(0);
            product.setId(99L);
            return 1;
        }).when(productMapper).insert(any(CoffeeProduct.class));

        Long productId = productService.createProduct(new SaveProductRequest(
                1L, " 生椰拿铁 ", "/uploads/a.png", 2600L, 100, 1, 1, List.of(2L, 3L)));

        assertThat(productId).isEqualTo(99L);
        ArgumentCaptor<CoffeeProduct> productCaptor = ArgumentCaptor.forClass(CoffeeProduct.class);
        verify(productMapper).insert(productCaptor.capture());
        assertThat(productCaptor.getValue().getName()).isEqualTo("生椰拿铁");
        assertThat(productCaptor.getValue().getPrice()).isEqualTo(2600L);

        ArgumentCaptor<CoffeeProductSpecGroup> bindingCaptor =
                ArgumentCaptor.forClass(CoffeeProductSpecGroup.class);
        verify(productSpecGroupMapper, times(2)).insert(bindingCaptor.capture());
        assertThat(bindingCaptor.getAllValues())
                .extracting(CoffeeProductSpecGroup::getGroupId)
                .containsExactly(2L, 3L);
        assertThat(bindingCaptor.getAllValues())
                .extracting(CoffeeProductSpecGroup::getSort)
                .containsExactly(1, 2);
    }

    @Test
    @DisplayName("售价必须大于 0")
    void createProductRejectsInvalidPrice() {
        assertThatThrownBy(() -> productService.createProduct(new SaveProductRequest(
                1L, "生椰拿铁", "", 0L, 100, 0, 1, List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("售价必须大于 0");
        verify(productMapper, never()).insert(any(CoffeeProduct.class));
    }

    @Test
    @DisplayName("分类必须存在")
    void createProductRejectsMissingCategory() {
        when(categoryMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> productService.createProduct(new SaveProductRequest(
                1L, "生椰拿铁", "", 2600L, 100, 0, 1, List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分类不存在");
        verify(productMapper, never()).insert(any(CoffeeProduct.class));
    }

    @Test
    @DisplayName("删除商品：软删商品并清理规格组绑定")
    void deleteProductRemovesBindings() {
        CoffeeProduct product = new CoffeeProduct();
        product.setId(5L);
        product.setName("美式咖啡");
        when(productMapper.selectById(5L)).thenReturn(product);

        productService.deleteProduct(5L);

        verify(productMapper).deleteById(5L);
        verify(productSpecGroupMapper).delete(any());
    }
}

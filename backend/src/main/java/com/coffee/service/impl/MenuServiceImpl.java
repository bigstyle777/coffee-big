package com.coffee.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.coffee.entity.CoffeeCategory;
import com.coffee.entity.CoffeeProduct;
import com.coffee.entity.CoffeeProductSpecGroup;
import com.coffee.entity.CoffeeSpecGroup;
import com.coffee.entity.CoffeeSpecOption;
import com.coffee.mapper.CoffeeCategoryMapper;
import com.coffee.mapper.CoffeeProductMapper;
import com.coffee.mapper.CoffeeProductSpecGroupMapper;
import com.coffee.mapper.CoffeeSpecGroupMapper;
import com.coffee.mapper.CoffeeSpecOptionMapper;
import com.coffee.service.MenuService;
import com.coffee.vo.menu.MenuCategoryVO;
import com.coffee.vo.menu.MenuProductVO;
import com.coffee.vo.menu.MenuSpecGroupVO;
import com.coffee.vo.menu.MenuSpecOptionVO;
import com.coffee.vo.menu.MenuVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 菜单业务实现：固定 5 次批量查询组装完整菜单树，避免 N+1
 */
@Service
@RequiredArgsConstructor
public class MenuServiceImpl implements MenuService {

    /** 分类启用 */
    private static final int CATEGORY_ENABLED = 1;
    /** 商品上架 */
    private static final int PRODUCT_ON_SHELF = 1;
    /** 规格选项启用 */
    private static final int SPEC_OPTION_ENABLED = 1;

    private final CoffeeCategoryMapper categoryMapper;
    private final CoffeeProductMapper productMapper;
    private final CoffeeProductSpecGroupMapper productSpecGroupMapper;
    private final CoffeeSpecGroupMapper specGroupMapper;
    private final CoffeeSpecOptionMapper specOptionMapper;

    @Override
    public MenuVO listMenu() {
        // 1. 上架分类
        List<CoffeeCategory> categories = categoryMapper.selectList(
                Wrappers.<CoffeeCategory>lambdaQuery()
                        .eq(CoffeeCategory::getStatus, CATEGORY_ENABLED)
                        .orderByAsc(CoffeeCategory::getSort)
                        .orderByAsc(CoffeeCategory::getId));
        if (categories.isEmpty()) {
            return new MenuVO(Collections.emptyList());
        }

        // 2. 分类下所有上架商品
        List<Long> categoryIds = categories.stream().map(CoffeeCategory::getId).toList();
        List<CoffeeProduct> products = productMapper.selectList(
                Wrappers.<CoffeeProduct>lambdaQuery()
                        .in(CoffeeProduct::getCategoryId, categoryIds)
                        .eq(CoffeeProduct::getStatus, PRODUCT_ON_SHELF)
                        .orderByAsc(CoffeeProduct::getSort)
                        .orderByAsc(CoffeeProduct::getId));
        if (products.isEmpty()) {
            List<MenuCategoryVO> emptyCategories = categories.stream()
                    .map(category -> new MenuCategoryVO(category.getId(), category.getName(), Collections.emptyList()))
                    .toList();
            return new MenuVO(emptyCategories);
        }

        // 3. 商品与规格组的绑定关系
        List<Long> productIds = products.stream().map(CoffeeProduct::getId).toList();
        List<CoffeeProductSpecGroup> bindings = productSpecGroupMapper.selectList(
                Wrappers.<CoffeeProductSpecGroup>lambdaQuery()
                        .in(CoffeeProductSpecGroup::getProductId, productIds)
                        .orderByAsc(CoffeeProductSpecGroup::getSort)
                        .orderByAsc(CoffeeProductSpecGroup::getId));
        Map<Long, List<Long>> groupIdsByProduct = bindings.stream()
                .collect(Collectors.groupingBy(
                        CoffeeProductSpecGroup::getProductId,
                        Collectors.mapping(CoffeeProductSpecGroup::getGroupId, Collectors.toList())));

        // 4. 规格组
        Set<Long> groupIds = bindings.stream()
                .map(CoffeeProductSpecGroup::getGroupId)
                .collect(Collectors.toSet());
        Map<Long, CoffeeSpecGroup> groupMap = groupIds.isEmpty()
                ? Collections.emptyMap()
                : specGroupMapper.selectList(
                        Wrappers.<CoffeeSpecGroup>lambdaQuery()
                                .in(CoffeeSpecGroup::getId, groupIds)
                                .orderByAsc(CoffeeSpecGroup::getSort)
                                .orderByAsc(CoffeeSpecGroup::getId))
                .stream()
                .collect(Collectors.toMap(CoffeeSpecGroup::getId, Function.identity()));

        // 5. 规格选项
        Map<Long, List<CoffeeSpecOption>> optionsByGroup = groupIds.isEmpty()
                ? Collections.emptyMap()
                : specOptionMapper.selectList(
                        Wrappers.<CoffeeSpecOption>lambdaQuery()
                                .in(CoffeeSpecOption::getGroupId, groupIds)
                                .eq(CoffeeSpecOption::getStatus, SPEC_OPTION_ENABLED)
                                .orderByAsc(CoffeeSpecOption::getSort)
                                .orderByAsc(CoffeeSpecOption::getId))
                .stream()
                .collect(Collectors.groupingBy(CoffeeSpecOption::getGroupId));

        // 6. 组装商品 VO
        Map<Long, List<CoffeeProduct>> productsByCategory = products.stream()
                .collect(Collectors.groupingBy(CoffeeProduct::getCategoryId));
        List<MenuCategoryVO> categoryVOs = categories.stream()
                .map(category -> new MenuCategoryVO(
                        category.getId(),
                        category.getName(),
                        productsByCategory.getOrDefault(category.getId(), Collections.emptyList())
                                .stream()
                                .map(product -> buildProductVO(product, groupIdsByProduct, groupMap, optionsByGroup))
                                .toList()))
                .toList();
        return new MenuVO(categoryVOs);
    }

    private MenuProductVO buildProductVO(CoffeeProduct product,
                                         Map<Long, List<Long>> groupIdsByProduct,
                                         Map<Long, CoffeeSpecGroup> groupMap,
                                         Map<Long, List<CoffeeSpecOption>> optionsByGroup) {
        List<MenuSpecGroupVO> specGroups = groupIdsByProduct
                .getOrDefault(product.getId(), Collections.emptyList())
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
                    // 规格组下没有任何可用选项时不展示，避免前端出现无法选择的必选组
                    if (options.isEmpty()) {
                        return null;
                    }
                    return new MenuSpecGroupVO(
                            group.getId(),
                            group.getName(),
                            isTrue(group.getRequired()),
                            isTrue(group.getMultiple()),
                            options);
                })
                .filter(Objects::nonNull)
                .toList();
        return new MenuProductVO(product.getId(), product.getName(), product.getPicUrl(),
                product.getPrice(), product.getStock(), specGroups);
    }

    private boolean isTrue(Integer flag) {
        return flag != null && flag == 1;
    }
}

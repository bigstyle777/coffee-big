package com.coffee.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.coffee.common.BusinessException;
import com.coffee.dto.admin.SaveProductRequest;
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
import com.coffee.service.ProductService;
import com.coffee.vo.admin.AdminCategoryVO;
import com.coffee.vo.admin.AdminProductDetailVO;
import com.coffee.vo.admin.AdminProductVO;
import com.coffee.vo.admin.AdminSpecGroupVO;
import com.coffee.vo.admin.AdminSpecOptionVO;
import com.coffee.vo.admin.PageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商品业务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    /** 商品上架 */
    private static final int STATUS_ON_SHELF = 1;
    /** 商品下架 */
    private static final int STATUS_OFF_SHELF = 0;
    /** 分页上限，避免一次拉取过多数据 */
    private static final long MAX_PAGE_SIZE = 100L;
    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_PIC_URL_LENGTH = 512;

    private final CoffeeProductMapper productMapper;
    private final CoffeeCategoryMapper categoryMapper;
    private final CoffeeProductSpecGroupMapper productSpecGroupMapper;
    private final CoffeeSpecGroupMapper specGroupMapper;
    private final CoffeeSpecOptionMapper specOptionMapper;

    @Override
    public List<AdminProductVO> listProducts() {
        List<CoffeeProduct> products = productMapper.selectList(
                Wrappers.<CoffeeProduct>lambdaQuery()
                        .orderByAsc(CoffeeProduct::getCategoryId)
                        .orderByAsc(CoffeeProduct::getSort)
                        .orderByAsc(CoffeeProduct::getId));
        return toProductVOs(products);
    }

    @Override
    public PageVO<AdminProductVO> pageProducts(long pageNum, long pageSize, String keyword,
                                               Long categoryId, Integer status) {
        long safePageNum = Math.max(pageNum, 1);
        long safePageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        if (status != null && status != STATUS_ON_SHELF && status != STATUS_OFF_SHELF) {
            throw new BusinessException("商品状态仅支持 1-上架 / 0-下架");
        }

        var wrapper = Wrappers.<CoffeeProduct>lambdaQuery()
                .like(StrUtil.isNotBlank(keyword), CoffeeProduct::getName,
                        keyword == null ? null : keyword.trim())
                .eq(categoryId != null, CoffeeProduct::getCategoryId, categoryId)
                .eq(status != null, CoffeeProduct::getStatus, status);
        long total = productMapper.selectCount(wrapper);
        if (total == 0) {
            return PageVO.empty(safePageNum, safePageSize);
        }

        long offset = (safePageNum - 1) * safePageSize;
        List<CoffeeProduct> products = productMapper.selectList(wrapper
                .orderByAsc(CoffeeProduct::getCategoryId)
                .orderByAsc(CoffeeProduct::getSort)
                .orderByAsc(CoffeeProduct::getId)
                .last("LIMIT " + safePageSize + " OFFSET " + offset));
        return new PageVO<>(total, safePageNum, safePageSize, toProductVOs(products));
    }

    @Override
    public AdminProductDetailVO getProductDetail(Long productId) {
        CoffeeProduct product = getExistingProduct(productId);
        CoffeeCategory category = categoryMapper.selectById(product.getCategoryId());
        List<Long> specGroupIds = productSpecGroupMapper.selectList(
                        Wrappers.<CoffeeProductSpecGroup>lambdaQuery()
                                .eq(CoffeeProductSpecGroup::getProductId, productId)
                                .orderByAsc(CoffeeProductSpecGroup::getSort))
                .stream()
                .map(CoffeeProductSpecGroup::getGroupId)
                .toList();
        return new AdminProductDetailVO(
                product.getId(),
                product.getCategoryId(),
                category == null ? "" : category.getName(),
                product.getName(),
                product.getPicUrl(),
                product.getPrice(),
                product.getStock(),
                product.getSort(),
                product.getStatus(),
                specGroupIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProduct(SaveProductRequest request) {
        validateRequest(request);
        ensureCategoryExists(request.categoryId());
        List<Long> specGroupIds = normalizeSpecGroupIds(request.specGroupIds());
        ensureSpecGroupsExist(specGroupIds);

        CoffeeProduct product = new CoffeeProduct();
        applyRequest(product, request);
        productMapper.insert(product);
        replaceSpecGroups(product.getId(), specGroupIds);
        log.info("商品新增成功 productId={}, name={}", product.getId(), product.getName());
        return product.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProduct(Long productId, SaveProductRequest request) {
        CoffeeProduct product = getExistingProduct(productId);
        validateRequest(request);
        ensureCategoryExists(request.categoryId());
        List<Long> specGroupIds = normalizeSpecGroupIds(request.specGroupIds());
        ensureSpecGroupsExist(specGroupIds);

        applyRequest(product, request);
        productMapper.updateById(product);
        replaceSpecGroups(productId, specGroupIds);
        log.info("商品编辑成功 productId={}, name={}", productId, product.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProduct(Long productId) {
        CoffeeProduct product = getExistingProduct(productId);
        productMapper.deleteById(productId);
        productSpecGroupMapper.delete(Wrappers.<CoffeeProductSpecGroup>lambdaQuery()
                .eq(CoffeeProductSpecGroup::getProductId, productId));
        log.info("商品已软删除 productId={}, name={}", productId, product.getName());
    }

    @Override
    public void updateProductStatus(Long productId, Integer status) {
        if (productId == null) {
            throw new BusinessException("商品 ID 不能为空");
        }
        if (status == null || (status != STATUS_ON_SHELF && status != STATUS_OFF_SHELF)) {
            throw new BusinessException("商品状态仅支持 1-上架 / 0-下架");
        }

        CoffeeProduct product = getExistingProduct(productId);
        CoffeeProduct updateProduct = new CoffeeProduct();
        updateProduct.setId(productId);
        updateProduct.setStatus(status);
        productMapper.updateById(updateProduct);
        log.info("商品状态更新成功 productId={}, {} -> {}", productId, product.getStatus(), status);
    }

    @Override
    public List<AdminCategoryVO> listCategories() {
        return categoryMapper.selectList(Wrappers.<CoffeeCategory>lambdaQuery()
                        .orderByAsc(CoffeeCategory::getSort)
                        .orderByAsc(CoffeeCategory::getId))
                .stream()
                .map(category -> new AdminCategoryVO(category.getId(), category.getName()))
                .toList();
    }

    @Override
    public List<AdminSpecGroupVO> listSpecGroups() {
        List<CoffeeSpecGroup> groups = specGroupMapper.selectList(
                Wrappers.<CoffeeSpecGroup>lambdaQuery()
                        .orderByAsc(CoffeeSpecGroup::getSort)
                        .orderByAsc(CoffeeSpecGroup::getId));
        if (groups.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, List<CoffeeSpecOption>> optionsByGroup = specOptionMapper.selectList(
                        Wrappers.<CoffeeSpecOption>lambdaQuery()
                                .in(CoffeeSpecOption::getGroupId,
                                        groups.stream().map(CoffeeSpecGroup::getId).toList())
                                .orderByAsc(CoffeeSpecOption::getSort)
                                .orderByAsc(CoffeeSpecOption::getId))
                .stream()
                .collect(Collectors.groupingBy(CoffeeSpecOption::getGroupId));

        return groups.stream()
                .map(group -> new AdminSpecGroupVO(
                        group.getId(),
                        group.getName(),
                        group.getRequired() != null && group.getRequired() == 1,
                        group.getMultiple() != null && group.getMultiple() == 1,
                        optionsByGroup.getOrDefault(group.getId(), Collections.emptyList())
                                .stream()
                                .map(option -> new AdminSpecOptionVO(
                                        option.getId(), option.getName(), option.getExtraPrice()))
                                .toList()))
                .toList();
    }

    private List<AdminProductVO> toProductVOs(List<CoffeeProduct> products) {
        if (products.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, CoffeeCategory> categoryMap = loadCategoryMap(products);
        return products.stream()
                .map(product -> {
                    CoffeeCategory category = categoryMap.get(product.getCategoryId());
                    return new AdminProductVO(
                            product.getId(),
                            product.getCategoryId(),
                            category == null ? "未分类" : category.getName(),
                            product.getName(),
                            product.getPicUrl(),
                            product.getPrice(),
                            product.getStock(),
                            product.getStatus());
                })
                .toList();
    }

    private Map<Long, CoffeeCategory> loadCategoryMap(Collection<CoffeeProduct> products) {
        Set<Long> categoryIds = products.stream()
                .map(CoffeeProduct::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (categoryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return categoryMapper.selectByIds(categoryIds).stream()
                .collect(Collectors.toMap(CoffeeCategory::getId, Function.identity()));
    }

    private CoffeeProduct getExistingProduct(Long productId) {
        if (productId == null) {
            throw new BusinessException("商品 ID 不能为空");
        }
        CoffeeProduct product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException("商品不存在或已删除");
        }
        return product;
    }

    private void validateRequest(SaveProductRequest request) {
        if (request == null) {
            throw new BusinessException("商品信息不能为空");
        }
        if (request.categoryId() == null) {
            throw new BusinessException("请选择商品分类");
        }
        if (StrUtil.isBlank(request.name())) {
            throw new BusinessException("商品名称不能为空");
        }
        if (request.name().trim().length() > MAX_NAME_LENGTH) {
            throw new BusinessException("商品名称不能超过 " + MAX_NAME_LENGTH + " 个字符");
        }
        if (request.price() == null || request.price() <= 0) {
            throw new BusinessException("商品售价必须大于 0");
        }
        if (request.stock() == null || request.stock() < 0) {
            throw new BusinessException("商品库存不能小于 0");
        }
        if (request.status() != null
                && request.status() != STATUS_ON_SHELF
                && request.status() != STATUS_OFF_SHELF) {
            throw new BusinessException("商品状态仅支持 1-上架 / 0-下架");
        }
        if (request.picUrl() != null && request.picUrl().length() > MAX_PIC_URL_LENGTH) {
            throw new BusinessException("图片地址过长");
        }
    }

    private void applyRequest(CoffeeProduct product, SaveProductRequest request) {
        product.setCategoryId(request.categoryId());
        product.setName(request.name().trim());
        product.setPicUrl(StrUtil.blankToDefault(request.picUrl(), ""));
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setSort(request.sort() == null ? 0 : request.sort());
        product.setStatus(request.status() == null ? STATUS_ON_SHELF : request.status());
    }

    private void ensureCategoryExists(Long categoryId) {
        CoffeeCategory category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException("商品分类不存在");
        }
    }

    private List<Long> normalizeSpecGroupIds(List<Long> specGroupIds) {
        if (specGroupIds == null || specGroupIds.isEmpty()) {
            return Collections.emptyList();
        }
        return specGroupIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private void ensureSpecGroupsExist(List<Long> specGroupIds) {
        if (specGroupIds.isEmpty()) {
            return;
        }
        long existingCount = specGroupMapper.selectCount(Wrappers.<CoffeeSpecGroup>lambdaQuery()
                .in(CoffeeSpecGroup::getId, specGroupIds));
        if (existingCount != specGroupIds.size()) {
            throw new BusinessException("部分规格组不存在，请刷新后重试");
        }
    }

    private void replaceSpecGroups(Long productId, List<Long> specGroupIds) {
        productSpecGroupMapper.delete(Wrappers.<CoffeeProductSpecGroup>lambdaQuery()
                .eq(CoffeeProductSpecGroup::getProductId, productId));
        for (int index = 0; index < specGroupIds.size(); index++) {
            CoffeeProductSpecGroup binding = new CoffeeProductSpecGroup();
            binding.setProductId(productId);
            binding.setGroupId(specGroupIds.get(index));
            binding.setSort(index + 1);
            productSpecGroupMapper.insert(binding);
        }
    }
}

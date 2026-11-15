package com.coffee.service;

import com.coffee.dto.admin.SaveProductRequest;
import com.coffee.vo.admin.AdminCategoryVO;
import com.coffee.vo.admin.AdminProductDetailVO;
import com.coffee.vo.admin.AdminProductVO;
import com.coffee.vo.admin.AdminSpecGroupVO;
import com.coffee.vo.admin.PageVO;

import java.util.List;

/**
 * 商品业务
 */
public interface ProductService {

    /**
     * 店长商品管理列表：包含已下架商品，按分类与排序值返回
     */
    List<AdminProductVO> listProducts();

    /**
     * 商品分页查询
     *
     * @param keyword    商品名称模糊搜索
     * @param categoryId 分类筛选，可为空
     * @param status     1-上架 / 0-下架，可为空
     */
    PageVO<AdminProductVO> pageProducts(long pageNum, long pageSize, String keyword,
                                        Long categoryId, Integer status);

    /**
     * 商品编辑回显，包含已绑定的规格组
     */
    AdminProductDetailVO getProductDetail(Long productId);

    /**
     * 新增商品并绑定规格组
     *
     * @return 新商品 ID
     */
    Long createProduct(SaveProductRequest request);

    /**
     * 编辑商品并覆盖规格组绑定
     */
    void updateProduct(Long productId, SaveProductRequest request);

    /**
     * 软删除商品：历史订单使用快照，不受影响
     */
    void deleteProduct(Long productId);

    /**
     * 店长一键上架 / 下架商品
     *
     * @param productId 商品 ID
     * @param status    1-上架 / 0-下架
     */
    void updateProductStatus(Long productId, Integer status);

    /**
     * 分类下拉项
     */
    List<AdminCategoryVO> listCategories();

    /**
     * 规格组及其选项，供商品编辑时勾选
     */
    List<AdminSpecGroupVO> listSpecGroups();
}

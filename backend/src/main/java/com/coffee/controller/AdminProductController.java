package com.coffee.controller;

import com.coffee.common.Result;
import com.coffee.dto.admin.SaveProductRequest;
import com.coffee.dto.admin.UpdateProductStatusRequest;
import com.coffee.service.ProductService;
import com.coffee.vo.admin.AdminCategoryVO;
import com.coffee.vo.admin.AdminProductDetailVO;
import com.coffee.vo.admin.AdminProductVO;
import com.coffee.vo.admin.AdminSpecGroupVO;
import com.coffee.vo.admin.PageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 店长后台商品管理接口
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;

    /**
     * 商品分页查询
     */
    @GetMapping("/product/page")
    public Result<PageVO<AdminProductVO>> pageProducts(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer status) {
        return Result.ok(productService.pageProducts(pageNum, pageSize, keyword, categoryId, status));
    }

    /**
     * 商品编辑回显
     */
    @GetMapping("/product/{productId}")
    public Result<AdminProductDetailVO> getProductDetail(@PathVariable Long productId) {
        return Result.ok(productService.getProductDetail(productId));
    }

    /**
     * 新增商品
     */
    @PostMapping("/product")
    public Result<Long> createProduct(@RequestBody SaveProductRequest request) {
        return Result.ok(productService.createProduct(request));
    }

    /**
     * 编辑商品
     */
    @PutMapping("/product/{productId}")
    public Result<Void> updateProduct(@PathVariable Long productId,
                                      @RequestBody SaveProductRequest request) {
        productService.updateProduct(productId, request);
        return Result.ok();
    }

    /**
     * 软删除商品
     */
    @DeleteMapping("/product/{productId}")
    public Result<Void> deleteProduct(@PathVariable Long productId) {
        productService.deleteProduct(productId);
        return Result.ok();
    }

    /**
     * 商品全部列表（包含已下架），供兼容旧版工作台使用
     */
    @GetMapping("/product/list")
    public Result<List<AdminProductVO>> listProducts() {
        return Result.ok(productService.listProducts());
    }

    /**
     * 商品一键上架 / 下架
     */
    @PutMapping("/product/status")
    public Result<Void> updateProductStatus(@RequestBody UpdateProductStatusRequest request) {
        productService.updateProductStatus(request.productId(), request.status());
        return Result.ok();
    }

    /**
     * 分类下拉项
     */
    @GetMapping("/category/list")
    public Result<List<AdminCategoryVO>> listCategories() {
        return Result.ok(productService.listCategories());
    }

    /**
     * 规格组与选项，供商品编辑时勾选
     */
    @GetMapping("/spec-group/list")
    public Result<List<AdminSpecGroupVO>> listSpecGroups() {
        return Result.ok(productService.listSpecGroups());
    }
}

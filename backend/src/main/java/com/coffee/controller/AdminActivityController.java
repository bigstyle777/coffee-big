package com.coffee.controller;

import com.coffee.common.Result;
import com.coffee.dto.admin.BindActivityProductRequest;
import com.coffee.dto.admin.SaveActivityRequest;
import com.coffee.dto.admin.UpdateActivityOpenRequest;
import com.coffee.dto.admin.UpdateActivityProductRequest;
import com.coffee.service.ActivityService;
import com.coffee.vo.admin.AdminActivityDetailVO;
import com.coffee.vo.admin.AdminActivityVO;
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

/**
 * 店长后台活动管理接口
 */
@RestController
@RequestMapping("/api/admin/activity")
@RequiredArgsConstructor
public class AdminActivityController {

    private final ActivityService activityService;

    /**
     * 活动分页查询
     */
    @GetMapping("/page")
    public Result<PageVO<AdminActivityVO>> pageActivities(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer isOpen) {
        return Result.ok(activityService.pageActivities(
                pageNum, pageSize, keyword, type, status, isOpen));
    }

    /**
     * 活动详情，包含绑定商品与预热状态
     */
    @GetMapping("/{activityId}")
    public Result<AdminActivityDetailVO> getActivityDetail(@PathVariable Long activityId) {
        return Result.ok(activityService.getActivityDetail(activityId));
    }

    /**
     * 新建活动，默认关闭
     */
    @PostMapping
    public Result<Long> createActivity(@RequestBody SaveActivityRequest request) {
        return Result.ok(activityService.createActivity(request));
    }

    /**
     * 编辑活动
     */
    @PutMapping("/{activityId}")
    public Result<Void> updateActivity(@PathVariable Long activityId,
                                       @RequestBody SaveActivityRequest request) {
        activityService.updateActivity(activityId, request);
        return Result.ok();
    }

    /**
     * 开启 / 关闭活动
     */
    @PutMapping("/{activityId}/open")
    public Result<Void> updateOpen(@PathVariable Long activityId,
                                   @RequestBody UpdateActivityOpenRequest request) {
        activityService.updateOpen(activityId, request == null ? null : request.isOpen());
        return Result.ok();
    }

    /**
     * 绑定活动商品
     */
    @PostMapping("/{activityId}/products")
    public Result<Void> bindProduct(@PathVariable Long activityId,
                                    @RequestBody BindActivityProductRequest request) {
        activityService.bindProduct(activityId, request);
        return Result.ok();
    }

    /**
     * 修改秒杀商品的活动价、限量与限购
     */
    @PutMapping("/{activityId}/products/{bindingId}")
    public Result<Void> updateActivityProduct(@PathVariable Long activityId,
                                              @PathVariable Long bindingId,
                                              @RequestBody UpdateActivityProductRequest request) {
        activityService.updateActivityProduct(activityId, bindingId, request);
        return Result.ok();
    }

    /**
     * 停用活动商品绑定，不物理删除
     */
    @DeleteMapping("/{activityId}/products/{bindingId}")
    public Result<Void> disableActivityProduct(@PathVariable Long activityId,
                                               @PathVariable Long bindingId) {
        activityService.disableActivityProduct(activityId, bindingId);
        return Result.ok();
    }

    /**
     * 一键预热秒杀库存
     */
    @PostMapping("/{activityId}/preheat")
    public Result<AdminActivityDetailVO> preheat(@PathVariable Long activityId) {
        return Result.ok(activityService.preheat(activityId));
    }

    /**
     * 查询预热状态
     */
    @GetMapping("/{activityId}/preheat-status")
    public Result<AdminActivityDetailVO> getPreheatStatus(@PathVariable Long activityId) {
        return Result.ok(activityService.getPreheatStatus(activityId));
    }
}

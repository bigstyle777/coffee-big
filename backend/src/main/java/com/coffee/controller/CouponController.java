package com.coffee.controller;

import com.coffee.common.Result;
import com.coffee.security.UserContext;
import com.coffee.service.CouponService;
import com.coffee.vo.coupon.CouponVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 顾客优惠券接口
 */
@RestController
@RequestMapping("/api/coupon")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    /**
     * 当前订单金额下可用的优惠券
     */
    @GetMapping("/available")
    public Result<List<CouponVO>> listAvailable(@RequestParam(required = false) Long amount) {
        return Result.ok(couponService.listAvailable(UserContext.getUserId(), amount));
    }
}

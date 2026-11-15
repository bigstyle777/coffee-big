package com.coffee.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.coffee.common.BusinessException;
import com.coffee.entity.CoffeeCoupon;
import com.coffee.enums.CouponStatus;
import com.coffee.mapper.CoffeeCouponMapper;
import com.coffee.service.CouponService;
import com.coffee.vo.coupon.CouponVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 优惠券业务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    /** 新客券名称，同时作为去重键 */
    private static final String WELCOME_COUPON_NAME = "新客立减 5 元";
    /** 新客券优惠金额（分） */
    private static final long WELCOME_DISCOUNT_AMOUNT = 500L;
    /** 新客券门槛（分） */
    private static final long WELCOME_MIN_AMOUNT = 2000L;
    /** 新客券有效期（天） */
    private static final long WELCOME_VALID_DAYS = 30L;

    private final CoffeeCouponMapper couponMapper;

    @Override
    public List<CouponVO> listAvailable(Long userId, Long amount) {
        if (userId == null) {
            throw new BusinessException("登录状态已失效，请重新登录");
        }
        // 老用户首次进入新版本时也能补发新客券
        grantWelcomeCouponIfNeeded(userId);

        long totalAmount = amount == null ? 0L : Math.max(amount, 0L);
        LocalDateTime now = LocalDateTime.now();
        return couponMapper.selectList(Wrappers.<CoffeeCoupon>lambdaQuery()
                        .eq(CoffeeCoupon::getUserId, userId)
                        .eq(CoffeeCoupon::getStatus, CouponStatus.UNUSED.name())
                        .le(CoffeeCoupon::getMinAmount, totalAmount)
                        .and(wrapper -> wrapper.isNull(CoffeeCoupon::getStartTime)
                                .or()
                                .le(CoffeeCoupon::getStartTime, now))
                        .and(wrapper -> wrapper.isNull(CoffeeCoupon::getEndTime)
                                .or()
                                .ge(CoffeeCoupon::getEndTime, now))
                        .orderByDesc(CoffeeCoupon::getDiscountAmount)
                        .orderByAsc(CoffeeCoupon::getEndTime))
                .stream()
                .map(coupon -> new CouponVO(
                        coupon.getId(),
                        coupon.getName(),
                        coupon.getDiscountAmount(),
                        coupon.getMinAmount(),
                        coupon.getEndTime()))
                .toList();
    }

    @Override
    public long calculateDiscount(Long userId, Long couponId, Long totalAmount) {
        if (couponId == null) {
            return 0L;
        }
        CoffeeCoupon coupon = couponMapper.selectById(couponId);
        if (coupon == null || !userId.equals(coupon.getUserId())) {
            throw new BusinessException("优惠券不存在");
        }
        if (!CouponStatus.UNUSED.name().equals(coupon.getStatus())) {
            throw new BusinessException("优惠券已使用或已失效");
        }
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getStartTime() != null && now.isBefore(coupon.getStartTime())) {
            throw new BusinessException("优惠券尚未生效");
        }
        if (coupon.getEndTime() != null && now.isAfter(coupon.getEndTime())) {
            throw new BusinessException("优惠券已过期");
        }
        long minAmount = coupon.getMinAmount() == null ? 0L : coupon.getMinAmount();
        if (totalAmount < minAmount) {
            throw new BusinessException("订单金额未达到优惠券使用门槛");
        }
        long discountAmount = coupon.getDiscountAmount() == null ? 0L : coupon.getDiscountAmount();
        return Math.min(discountAmount, totalAmount);
    }

    @Override
    public void markUsed(Long userId, Long couponId, Long orderId) {
        int updatedRows = couponMapper.markUsed(couponId, userId, orderId);
        if (updatedRows == 0) {
            throw new BusinessException("优惠券已被使用，请重新选择");
        }
    }

    @Override
    public void restore(Long couponId, Long orderId) {
        couponMapper.restore(couponId, orderId);
    }

    @Override
    public void grantWelcomeCouponIfNeeded(Long userId) {
        Long existing = couponMapper.selectCount(
                Wrappers.<CoffeeCoupon>lambdaQuery().eq(CoffeeCoupon::getUserId, userId));
        if (existing != null && existing > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        CoffeeCoupon coupon = new CoffeeCoupon();
        coupon.setUserId(userId);
        coupon.setName(WELCOME_COUPON_NAME);
        coupon.setDiscountAmount(WELCOME_DISCOUNT_AMOUNT);
        coupon.setMinAmount(WELCOME_MIN_AMOUNT);
        coupon.setStatus(CouponStatus.UNUSED.name());
        coupon.setStartTime(now);
        coupon.setEndTime(now.plusDays(WELCOME_VALID_DAYS));
        try {
            couponMapper.insert(coupon);
            log.info("新客优惠券发放成功 userId={}", userId);
        } catch (DuplicateKeyException exception) {
            // 并发首次登录时唯一索引兜底，忽略重复发放
            log.debug("新客优惠券已存在 userId={}", userId);
        }
    }
}

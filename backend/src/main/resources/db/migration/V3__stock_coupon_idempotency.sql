-- ============================================================
-- V3：真实下单链路所需结构
--   1. 商品库存
--   2. 订单优惠与客户端幂等键
--   3. 优惠券
-- ============================================================

-- 1. 商品库存（普通下单直接扣减，秒杀场景才需要 Redis 预扣）
ALTER TABLE `coffee_product`
    ADD COLUMN `stock` INT NOT NULL DEFAULT 0 COMMENT '库存（杯）' AFTER `price`;

-- 给种子商品一个初始库存，方便直接体验
UPDATE `coffee_product`
SET `stock` = 100
WHERE `stock` = 0;

-- 2. 订单优惠与幂等
ALTER TABLE `coffee_order`
    ADD COLUMN `discount_amount` BIGINT NOT NULL DEFAULT 0 COMMENT '优惠金额（分）' AFTER `total_amount`,
    ADD COLUMN `coupon_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '使用的优惠券 ID' AFTER `discount_amount`,
    ADD COLUMN `request_id` VARCHAR(64) DEFAULT NULL COMMENT '客户端幂等键，防止重复提交' AFTER `coupon_id`,
    ADD UNIQUE KEY `uk_user_request` (`user_id`, `request_id`);

-- 3. 优惠券（当前按用户发放固定金额券，后续可扩展折扣券/活动券）
CREATE TABLE IF NOT EXISTS `coffee_coupon`
(
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`         BIGINT UNSIGNED NOT NULL COMMENT '所属用户 ID',
    `name`            VARCHAR(64)     NOT NULL COMMENT '优惠券名称',
    `discount_amount` BIGINT          NOT NULL DEFAULT 0 COMMENT '优惠金额（分）',
    `min_amount`      BIGINT          NOT NULL DEFAULT 0 COMMENT '使用门槛（分），0 表示无门槛',
    `status`          VARCHAR(16)     NOT NULL DEFAULT 'UNUSED' COMMENT '状态：UNUSED-未使用 / USED-已使用 / EXPIRED-已过期',
    `start_time`      DATETIME(3)              DEFAULT NULL COMMENT '生效时间',
    `end_time`        DATETIME(3)              DEFAULT NULL COMMENT '过期时间',
    `used_order_id`   BIGINT UNSIGNED          DEFAULT NULL COMMENT '使用的订单 ID',
    `used_time`       DATETIME(3)              DEFAULT NULL COMMENT '使用时间',
    `create_time`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted`         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 / 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_name` (`user_id`, `name`),
    KEY `idx_user_status` (`user_id`, `status`),
    KEY `idx_end_time` (`end_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='用户优惠券';

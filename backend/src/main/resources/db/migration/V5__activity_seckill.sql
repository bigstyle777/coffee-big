-- ============================================================
-- V5：营销活动与秒杀
-- 设计约定：
--   1. 活动只关闭不删除，因此活动表不设 deleted 字段
--   2. 活动商品绑定采用 status=0 停用，不物理删除，保留对账数据
--   3. activity_stock 是营销限量，秒杀下单仍会扣减 coffee_product.stock
--   4. 金额单位统一为「分」
-- ============================================================

CREATE TABLE IF NOT EXISTS `coffee_activity`
(
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `title`         VARCHAR(64)     NOT NULL COMMENT '活动名称',
    `type`          VARCHAR(16)     NOT NULL COMMENT '活动类型：SECKILL-秒杀 / FULL_REDUCTION-满减',
    `start_time`    DATETIME(3)     NOT NULL COMMENT '开始时间',
    `end_time`      DATETIME(3)     NOT NULL COMMENT '结束时间',
    `is_open`       TINYINT         NOT NULL DEFAULT 0 COMMENT '开关：1-开启 / 0-关闭；结束只关闭不删除',
    `full_amount`   BIGINT          NOT NULL DEFAULT 0 COMMENT '满减门槛（分），仅满减使用',
    `reduce_amount` BIGINT          NOT NULL DEFAULT 0 COMMENT '满减金额（分），仅满减使用',
    `remark`        VARCHAR(255)    NOT NULL DEFAULT '' COMMENT '备注',
    `create_time`   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_open_time` (`is_open`, `start_time`, `end_time`),
    KEY `idx_type_open` (`type`, `is_open`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='营销活动（只关闭，不删除）';

CREATE TABLE IF NOT EXISTS `coffee_activity_product`
(
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `activity_id`    BIGINT UNSIGNED NOT NULL COMMENT '活动 ID',
    `product_id`     BIGINT UNSIGNED NOT NULL COMMENT '商品 ID',
    `activity_price` BIGINT          NOT NULL DEFAULT 0 COMMENT '秒杀基础价（分/杯），规格加价另计',
    `activity_stock` INT             NOT NULL DEFAULT 0 COMMENT '活动限量（杯），必须不大于商品库存',
    `sold_count`     INT             NOT NULL DEFAULT 0 COMMENT '已售（杯），DB 兜底与对账',
    `limit_per_user` INT             NOT NULL DEFAULT 1 COMMENT '每人限购（杯）',
    `status`         TINYINT         NOT NULL DEFAULT 1 COMMENT '绑定状态：1-生效 / 0-停用，不物理删除',
    `sort`           INT             NOT NULL DEFAULT 0 COMMENT '排序值',
    `create_time`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_activity_product` (`activity_id`, `product_id`),
    KEY `idx_product_activity` (`product_id`, `activity_id`),
    KEY `idx_activity_status_sort` (`activity_id`, `status`, `sort`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='活动-商品绑定（不物理删除）';

-- 订单扩展：秒杀订单类型、使用的活动、活动优惠金额
ALTER TABLE `coffee_order`
    ADD COLUMN `order_type` VARCHAR(16) NOT NULL DEFAULT 'NORMAL'
        COMMENT '订单类型：NORMAL-普通 / SECKILL-秒杀' AFTER `status`,
    ADD COLUMN `activity_id` BIGINT UNSIGNED DEFAULT NULL
        COMMENT '使用的活动 ID' AFTER `coupon_id`,
    ADD COLUMN `activity_discount_amount` BIGINT NOT NULL DEFAULT 0
        COMMENT '活动优惠金额（分）' AFTER `discount_amount`,
    ADD KEY `idx_activity` (`activity_id`),
    ADD KEY `idx_type_create` (`order_type`, `create_time`);

-- 订单明细扩展：记录秒杀活动商品，取消时用于精确恢复活动库存
ALTER TABLE `coffee_order_item`
    ADD COLUMN `activity_product_id` BIGINT UNSIGNED DEFAULT NULL
        COMMENT '秒杀活动商品 ID，普通订单为空' AFTER `product_id`,
    ADD KEY `idx_activity_product` (`activity_product_id`);

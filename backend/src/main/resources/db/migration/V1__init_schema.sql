-- ============================================================
-- V1：初始化数据库结构（MySQL 8.0+）
-- 由 Flyway 在应用启动时自动执行，无需手工建表
-- 约定：
--   1. 所有金额字段单位为「分」，类型 BIGINT，禁止使用浮点类型
--   2. 统一 utf8mb4_0900_ai_ci + InnoDB
--   3. 逻辑删除统一字段 deleted（0 正常 / 1 已删除），配合 MyBatis-Plus @TableLogic
--   4. 不建物理外键，关联完整性由应用层保证，便于后续迁移与分库
--   5. 主键统一 BIGINT UNSIGNED 自增
-- ============================================================

-- ------------------------------------------------------------
-- 1. 用户表（顾客与店长共用，通过 role 区分）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `openid`      VARCHAR(64)     NOT NULL COMMENT '微信 openid',
    `nickname`    VARCHAR(64)     NOT NULL DEFAULT '' COMMENT '昵称',
    `avatar`      VARCHAR(512)    NOT NULL DEFAULT '' COMMENT '头像地址',
    `phone`       VARCHAR(20)              DEFAULT NULL COMMENT '手机号',
    `role`        VARCHAR(16)     NOT NULL DEFAULT 'CUSTOMER' COMMENT '角色：CUSTOMER-顾客 / ADMIN-店长',
    `status`      TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：1-正常 / 0-禁用',
    `create_time` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted`     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 / 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_openid` (`openid`),
    KEY `idx_phone` (`phone`),
    KEY `idx_role` (`role`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='用户表（顾客 / 店长）';

-- ------------------------------------------------------------
-- 2. 商品分类表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `coffee_category`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(32)     NOT NULL COMMENT '分类名称，如 经典咖啡 / 拿铁 / 茶饮',
    `sort`        INT             NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
    `status`      TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：1-启用 / 0-停用',
    `create_time` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted`     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 / 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_status_sort` (`status`, `sort`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='商品分类表';

-- ------------------------------------------------------------
-- 3. 商品表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `coffee_product`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `category_id` BIGINT UNSIGNED NOT NULL COMMENT '所属分类 ID',
    `name`        VARCHAR(64)     NOT NULL COMMENT '商品名称，如 生椰拿铁',
    `pic_url`     VARCHAR(512)    NOT NULL DEFAULT '' COMMENT '商品主图地址',
    `price`       BIGINT          NOT NULL DEFAULT 0 COMMENT '基础价格（分），不含规格加价',
    `sort`        INT             NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
    `status`      TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：1-上架 / 0-下架',
    `create_time` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted`     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 / 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_category_status_sort` (`category_id`, `status`, `sort`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='商品表';

-- ------------------------------------------------------------
-- 4. 规格组表（杯型 / 温度 / 糖度 / 加料）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `coffee_spec_group`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(32)     NOT NULL COMMENT '规格组名称，如 杯型 / 温度 / 糖度 / 加料',
    `required`    TINYINT         NOT NULL DEFAULT 1 COMMENT '是否必选：1-必选 / 0-可选',
    `multiple`    TINYINT         NOT NULL DEFAULT 0 COMMENT '是否多选：1-多选 / 0-单选',
    `sort`        INT             NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
    `create_time` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted`     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 / 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='规格组表';

-- ------------------------------------------------------------
-- 5. 规格选项表（大杯 / 热 / 半糖 / 燕麦奶……含加价）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `coffee_spec_option`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `group_id`    BIGINT UNSIGNED NOT NULL COMMENT '所属规格组 ID',
    `name`        VARCHAR(32)     NOT NULL COMMENT '选项名称，如 大杯 / 热 / 半糖 / 燕麦奶',
    `extra_price` BIGINT          NOT NULL DEFAULT 0 COMMENT '加价（分），0 表示不加价',
    `sort`        INT             NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
    `status`      TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：1-启用 / 0-停用',
    `create_time` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted`     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 / 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_name` (`group_id`, `name`),
    KEY `idx_group_sort` (`group_id`, `sort`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='规格选项表';

-- ------------------------------------------------------------
-- 6. 商品-规格组绑定表（控制某商品可用哪些规格及展示顺序）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `coffee_product_spec_group`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `product_id`  BIGINT UNSIGNED NOT NULL COMMENT '商品 ID',
    `group_id`    BIGINT UNSIGNED NOT NULL COMMENT '规格组 ID',
    `sort`        INT             NOT NULL DEFAULT 0 COMMENT '该商品下规格组的展示顺序',
    `create_time` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_group` (`product_id`, `group_id`),
    KEY `idx_group_id` (`group_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='商品-规格组绑定表';

-- ------------------------------------------------------------
-- 7. 订单主表
--    状态机：PENDING_PAY -> MAKING -> READY -> COMPLETED
--             PENDING_PAY / MAKING 可流转至 CANCELLED
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `coffee_order`
(
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no`     VARCHAR(32)     NOT NULL COMMENT '业务订单号（如 20261114153000123456），全局唯一',
    `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '下单用户 ID',
    `status`       VARCHAR(24)     NOT NULL DEFAULT 'PENDING_PAY' COMMENT '订单状态：PENDING_PAY-待支付 / MAKING-制作中 / READY-待取餐 / COMPLETED-已完成 / CANCELLED-已取消',
    `pickup_type`  VARCHAR(16)     NOT NULL DEFAULT 'SELF_PICKUP' COMMENT '取餐方式：SELF_PICKUP-到店自取 / DINE_IN-堂食',
    `total_amount` BIGINT          NOT NULL DEFAULT 0 COMMENT '商品总额（分），下单时后端按库重算',
    `pay_amount`   BIGINT          NOT NULL DEFAULT 0 COMMENT '实付金额（分），禁止取用前端传值',
    `item_count`   INT             NOT NULL DEFAULT 0 COMMENT '商品总件数',
    `pickup_code`  VARCHAR(8)               DEFAULT NULL COMMENT '取餐码（下单时生成，如 A001）',
    `remark`       VARCHAR(255)    NOT NULL DEFAULT '' COMMENT '顾客备注，如 少冰、不要吸管',
    `pay_time`     DATETIME(3)              DEFAULT NULL COMMENT '支付时间',
    `finish_time`  DATETIME(3)              DEFAULT NULL COMMENT '订单完成时间',
    `cancel_time`  DATETIME(3)              DEFAULT NULL COMMENT '订单取消时间',
    `create_time`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted`      TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 / 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_status` (`user_id`, `status`),
    KEY `idx_status_create` (`status`, `create_time`),
    KEY `idx_pickup_code` (`pickup_code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='订单主表';

-- ------------------------------------------------------------
-- 8. 订单明细表（下单即快照，商品改价/下架不影响历史订单）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `coffee_order_item`
(
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`         BIGINT UNSIGNED NOT NULL COMMENT '订单 ID',
    `product_id`       BIGINT UNSIGNED NOT NULL COMMENT '商品 ID（仅引用）',
    `product_name`     VARCHAR(64)     NOT NULL COMMENT '商品名称快照',
    `product_pic`      VARCHAR(512)    NOT NULL DEFAULT '' COMMENT '商品图片快照',
    `base_price`       BIGINT          NOT NULL DEFAULT 0 COMMENT '基础单价快照（分）',
    `spec_extra_price` BIGINT          NOT NULL DEFAULT 0 COMMENT '规格加价合计（分/件）',
    `spec_text`        VARCHAR(255)    NOT NULL DEFAULT '' COMMENT '规格展示文案快照，如 大杯 / 热 / 半糖',
    `unit_price`       BIGINT          NOT NULL DEFAULT 0 COMMENT '单件价格（分）= 基础单价 + 规格加价',
    `quantity`         INT             NOT NULL DEFAULT 1 COMMENT '购买数量',
    `subtotal_amount`  BIGINT          NOT NULL DEFAULT 0 COMMENT '小计金额（分）= 单件价格 × 数量',
    `spec_json`        JSON                     DEFAULT NULL COMMENT '所选规格快照：[{groupId,groupName,optionId,optionName,extraPrice}]',
    `create_time`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_product_id` (`product_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='订单明细表（只追加，不修改）';

-- ------------------------------------------------------------
-- 9. 订单状态流水表（只追加，记录每一次状态流转，用于审计与追溯）
--    写入时机：事务提交后异步写入，避免主事务回滚产生脏流水
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `coffee_order_log`
(
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`      BIGINT UNSIGNED NOT NULL COMMENT '订单 ID',
    `from_status`   VARCHAR(24)              DEFAULT NULL COMMENT '变更前状态，创建订单时为 NULL',
    `to_status`     VARCHAR(24)     NOT NULL COMMENT '变更后状态',
    `operator_id`   BIGINT UNSIGNED          DEFAULT NULL COMMENT '操作人用户 ID，系统自动流转时为 NULL',
    `operator_role` VARCHAR(16)     NOT NULL DEFAULT 'SYSTEM' COMMENT '操作人角色：CUSTOMER / ADMIN / SYSTEM',
    `remark`        VARCHAR(255)    NOT NULL DEFAULT '' COMMENT '操作备注，如 超时未支付自动取消',
    `create_time`   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_id_create` (`order_id`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='订单状态流水表（只追加）';

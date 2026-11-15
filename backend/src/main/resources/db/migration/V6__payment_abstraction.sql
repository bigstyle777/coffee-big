-- ============================================================
-- V6：支付抽象层
--   1. 订单增加支付渠道、预支付 ID、第三方交易号
--   2. 订单增加退款状态与退款信息，用于后台对账
--   3. 默认 pay_channel=MOCK，兼容已有模拟支付订单
-- ============================================================

ALTER TABLE `coffee_order`
    ADD COLUMN `pay_channel` VARCHAR(16) NOT NULL DEFAULT 'MOCK'
        COMMENT '支付渠道：MOCK-模拟支付 / WECHAT-微信支付' AFTER `pay_time`,
    ADD COLUMN `prepay_id` VARCHAR(64) DEFAULT NULL
        COMMENT '支付平台预支付 ID' AFTER `pay_channel`,
    ADD COLUMN `transaction_id` VARCHAR(64) DEFAULT NULL
        COMMENT '支付平台交易号' AFTER `prepay_id`,
    ADD COLUMN `refund_status` VARCHAR(16) NOT NULL DEFAULT 'NONE'
        COMMENT '退款状态：NONE / REFUNDING / REFUNDED / FAILED' AFTER `transaction_id`,
    ADD COLUMN `refund_no` VARCHAR(64) DEFAULT NULL
        COMMENT '退款单号' AFTER `refund_status`,
    ADD COLUMN `refund_amount` BIGINT NOT NULL DEFAULT 0
        COMMENT '退款金额（分）' AFTER `refund_no`,
    ADD COLUMN `refund_time` DATETIME(3) DEFAULT NULL
        COMMENT '退款时间' AFTER `refund_amount`,
    ADD UNIQUE KEY `uk_transaction_id` (`transaction_id`);

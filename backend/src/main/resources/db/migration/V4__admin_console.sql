-- ============================================================
-- V4：店长后台账号
-- 说明：
--   1. 后台账号与小程序用户完全隔离，浏览器后台使用账号密码登录
--   2. admin-token 写入 Redis 白名单，支持退出登录立即失效
--   3. 初始账号由 AdminBootstrapRunner 在启动时创建，脚本不写死密码
-- ============================================================

CREATE TABLE IF NOT EXISTS `sys_admin`
(
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`        VARCHAR(32)     NOT NULL COMMENT '登录账号',
    `password_hash`   VARCHAR(100)    NOT NULL COMMENT 'BCrypt 密码哈希',
    `nickname`        VARCHAR(32)     NOT NULL DEFAULT '店长' COMMENT '显示名称',
    `status`          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：1-正常 / 0-禁用',
    `last_login_time` DATETIME(3)              DEFAULT NULL COMMENT '最近登录时间',
    `create_time`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted`         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 / 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='店长后台账号';

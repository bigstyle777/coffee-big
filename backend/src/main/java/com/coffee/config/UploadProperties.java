package com.coffee.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本地图片存储配置
 *
 * @param dir          图片落盘目录，支持相对路径与绝对路径
 * @param publicPrefix 对外访问前缀，如 /uploads/
 */
@ConfigurationProperties(prefix = "coffee.upload")
public record UploadProperties(String dir, String publicPrefix) {
}

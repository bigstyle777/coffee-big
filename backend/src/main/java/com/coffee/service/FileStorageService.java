package com.coffee.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储
 */
public interface FileStorageService {

    /**
     * 保存商品图片，返回可直接访问的相对地址
     */
    String storeImage(MultipartFile file);
}

package com.coffee.controller;

import com.coffee.common.Result;
import com.coffee.service.FileStorageService;
import com.coffee.vo.admin.UploadVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 店长后台上传接口
 */
@RestController
@RequestMapping("/api/admin/upload")
@RequiredArgsConstructor
public class AdminUploadController {

    private final FileStorageService fileStorageService;

    /**
     * 上传商品图片，返回可直接访问的相对地址
     */
    @PostMapping("/image")
    public Result<UploadVO> uploadImage(@RequestPart("file") MultipartFile file) {
        return Result.ok(new UploadVO(fileStorageService.storeImage(file)));
    }
}

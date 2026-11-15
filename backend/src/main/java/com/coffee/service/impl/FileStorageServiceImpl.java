package com.coffee.service.impl;

import cn.hutool.core.util.StrUtil;
import com.coffee.common.BusinessException;
import com.coffee.config.UploadProperties;
import com.coffee.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 本地磁盘图片存储
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageServiceImpl implements FileStorageService {

    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    private final UploadProperties uploadProperties;

    @Override
    public String storeImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的图片");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new BusinessException("图片不能超过 5MB");
        }
        String extension = resolveExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("仅支持 jpg / jpeg / png / webp / gif 格式");
        }

        String monthPath = LocalDate.now().format(MONTH_FORMATTER);
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path targetDirectory = Paths.get(uploadProperties.dir())
                .toAbsolutePath()
                .normalize()
                .resolve(monthPath);
        try {
            Files.createDirectories(targetDirectory);
            file.transferTo(targetDirectory.resolve(filename));
        } catch (IOException exception) {
            log.error("商品图片保存失败 filename={}", file.getOriginalFilename(), exception);
            throw new BusinessException("图片保存失败，请稍后重试");
        }

        String publicPrefix = StrUtil.nullToDefault(uploadProperties.publicPrefix(), "/uploads/");
        if (!publicPrefix.endsWith("/")) {
            publicPrefix = publicPrefix + "/";
        }
        String url = publicPrefix + monthPath + "/" + filename;
        log.info("商品图片上传成功 url={}, size={}字节", url, file.getSize());
        return url;
    }

    private String resolveExtension(String originalFilename) {
        if (StrUtil.isBlank(originalFilename)) {
            throw new BusinessException("图片文件名不能为空");
        }
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == originalFilename.length() - 1) {
            throw new BusinessException("图片格式不正确");
        }
        return originalFilename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }
}

package com.coffee.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.coffee.common.BusinessException;
import com.coffee.common.Result;
import com.coffee.dto.admin.AdminLoginRequest;
import com.coffee.entity.SysAdmin;
import com.coffee.mapper.SysAdminMapper;
import com.coffee.security.AdminContext;
import com.coffee.security.AdminToken;
import com.coffee.security.AdminTokenStore;
import com.coffee.security.JwtUtils;
import com.coffee.service.AdminAuthService;
import com.coffee.vo.admin.AdminInfoVO;
import com.coffee.vo.admin.AdminLoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 店长后台认证实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthServiceImpl implements AdminAuthService {

    /** 后台账号正常状态 */
    private static final int ADMIN_ENABLED = 1;
    /** 白名单最短有效期，避免时钟误差导致刚登录就失效 */
    private static final long MIN_TOKEN_TTL_SECONDS = 60L;

    private final SysAdminMapper adminMapper;
    private final JwtUtils jwtUtils;
    private final AdminTokenStore adminTokenStore;

    @Override
    public AdminLoginVO login(AdminLoginRequest request) {
        if (request == null || StrUtil.isBlank(request.username()) || StrUtil.isBlank(request.password())) {
            throw new BusinessException("请输入账号和密码");
        }
        String username = request.username().trim();
        SysAdmin admin = adminMapper.selectOne(Wrappers.<SysAdmin>lambdaQuery()
                .eq(SysAdmin::getUsername, username));
        // 账号不存在与密码错误使用同一提示，避免暴露账号是否存在
        if (admin == null || !BCrypt.checkpw(request.password(), admin.getPasswordHash())) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "账号或密码错误");
        }
        if (admin.getStatus() == null || admin.getStatus() != ADMIN_ENABLED) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "账号已被禁用，请联系管理员");
        }

        AdminToken adminToken = jwtUtils.generateAdminToken(admin.getId(), admin.getUsername());
        long ttlSeconds = Duration.between(LocalDateTime.now(), adminToken.expiresAt()).getSeconds();
        adminTokenStore.save(adminToken.tokenId(), admin.getId(),
                Duration.ofSeconds(Math.max(ttlSeconds, MIN_TOKEN_TTL_SECONDS)));

        SysAdmin updateAdmin = new SysAdmin();
        updateAdmin.setId(admin.getId());
        updateAdmin.setLastLoginTime(LocalDateTime.now());
        adminMapper.updateById(updateAdmin);

        log.info("店长后台登录成功 adminId={}, username={}", admin.getId(), admin.getUsername());
        return new AdminLoginVO(adminToken.token(),
                new AdminInfoVO(admin.getId(), admin.getUsername(), admin.getNickname()));
    }

    @Override
    public void logout() {
        String tokenId = AdminContext.getTokenId();
        adminTokenStore.remove(tokenId);
        log.info("店长后台退出登录 username={}", AdminContext.getUsername());
    }

    @Override
    public AdminInfoVO getCurrentAdmin() {
        SysAdmin admin = adminMapper.selectById(AdminContext.getAdminId());
        if (admin == null) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "账号不存在，请重新登录");
        }
        if (admin.getStatus() == null || admin.getStatus() != ADMIN_ENABLED) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "账号已被禁用");
        }
        return new AdminInfoVO(admin.getId(), admin.getUsername(), admin.getNickname());
    }
}

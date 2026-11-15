package com.coffee.service;

import cn.hutool.crypto.digest.BCrypt;
import com.coffee.common.BusinessException;
import com.coffee.common.Result;
import com.coffee.dto.admin.AdminLoginRequest;
import com.coffee.entity.SysAdmin;
import com.coffee.mapper.SysAdminMapper;
import com.coffee.security.AdminToken;
import com.coffee.security.AdminTokenStore;
import com.coffee.security.JwtUtils;
import com.coffee.service.impl.AdminAuthServiceImpl;
import com.coffee.vo.admin.AdminLoginVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 店长后台登录单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminAuthServiceImplTest {

    @Mock
    private SysAdminMapper adminMapper;
    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private AdminTokenStore adminTokenStore;

    private AdminAuthServiceImpl adminAuthService;
    private SysAdmin admin;

    @BeforeEach
    void setUp() {
        adminAuthService = new AdminAuthServiceImpl(adminMapper, jwtUtils, adminTokenStore);
        admin = new SysAdmin();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setNickname("店长");
        admin.setPasswordHash(BCrypt.hashpw("test-password"));
        admin.setStatus(1);
    }

    @Test
    @DisplayName("登录成功：签发 token 并写入 Redis 白名单")
    void loginSavesTokenWhitelist() {
        when(adminMapper.selectOne(any())).thenReturn(admin);
        when(jwtUtils.generateAdminToken(1L, "admin"))
                .thenReturn(new AdminToken("jwt-token", "token-id", LocalDateTime.now().plusHours(12)));

        AdminLoginVO result = adminAuthService.login(new AdminLoginRequest("admin", "test-password"));

        assertThat(result.token()).isEqualTo("jwt-token");
        assertThat(result.admin().username()).isEqualTo("admin");
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(adminTokenStore).save(eq("token-id"), eq(1L), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue().toSeconds()).isGreaterThan(0);
        verify(adminMapper).updateById(any(SysAdmin.class));
    }

    @Test
    @DisplayName("密码错误：返回 401，且不签发 token")
    void loginRejectsWrongPassword() {
        when(adminMapper.selectOne(any())).thenReturn(admin);

        assertThatThrownBy(() -> adminAuthService.login(new AdminLoginRequest("admin", "wrong-password")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCode())
                .isEqualTo(Result.UNAUTHORIZED_CODE);
        verify(jwtUtils, never()).generateAdminToken(any(Long.class), anyString());
    }

    @Test
    @DisplayName("账号禁用：返回 403")
    void loginRejectsDisabledAccount() {
        admin.setStatus(0);
        when(adminMapper.selectOne(any())).thenReturn(admin);

        assertThatThrownBy(() -> adminAuthService.login(new AdminLoginRequest("admin", "test-password")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCode())
                .isEqualTo(Result.FORBIDDEN_CODE);
    }
}

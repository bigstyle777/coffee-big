package com.coffee.security;

import com.coffee.common.BusinessException;
import com.coffee.common.Result;
import com.coffee.config.AdminProperties;
import com.coffee.config.AuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 工具：负责签发与校验 Token
 * 通过 scope 声明区分两类凭证：
 *   CUSTOMER —— 小程序用户 token，只能访问顾客接口
 *   ADMIN    —— 浏览器后台 admin-token，只能访问 /api/admin/**
 */
@Component
public class JwtUtils {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_ADMIN_ID = "adminId";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_SCOPE = "scope";
    private static final String SCOPE_CUSTOMER = "CUSTOMER";
    private static final String SCOPE_ADMIN = "ADMIN";
    private static final int MIN_SECRET_LENGTH = 32;

    private final SecretKey secretKey;
    private final long expireHours;
    private final long adminExpireHours;

    public JwtUtils(AuthProperties properties, AdminProperties adminProperties) {
        String secret = properties.jwtSecret();
        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("JWT 密钥长度不能少于 " + MIN_SECRET_LENGTH + " 位");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireHours = properties.jwtExpireHours();
        this.adminExpireHours = adminProperties.tokenExpireHours();
    }

    /**
     * 签发小程序用户 Token
     */
    public String generateToken(Long userId, String role) {
        Date issuedAt = new Date();
        Date expiresAt = new Date(issuedAt.getTime() + expireHours * 60 * 60 * 1000);
        return Jwts.builder()
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_SCOPE, SCOPE_CUSTOMER)
                .issuedAt(issuedAt)
                .expiration(expiresAt)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 签发店长后台 Token
     * tokenId 会写入 Redis 白名单，退出登录时删除即可立即失效
     */
    public AdminToken generateAdminToken(Long adminId, String username) {
        Date issuedAt = new Date();
        Date expiresAt = new Date(issuedAt.getTime() + adminExpireHours * 60 * 60 * 1000);
        String tokenId = UUID.randomUUID().toString().replace("-", "");
        String token = Jwts.builder()
                .id(tokenId)
                .claim(CLAIM_ADMIN_ID, adminId)
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_SCOPE, SCOPE_ADMIN)
                .issuedAt(issuedAt)
                .expiration(expiresAt)
                .signWith(secretKey)
                .compact();
        return new AdminToken(token, tokenId, toLocalDateTime(expiresAt));
    }

    /**
     * 解析小程序用户 Token，失败统一抛出 401 业务异常
     */
    public LoginUser parseLoginUser(String token) {
        Claims claims = parseClaims(token);
        if (!SCOPE_CUSTOMER.equals(claims.get(CLAIM_SCOPE, String.class))) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "请使用小程序账号登录");
        }
        Object userIdValue = claims.get(CLAIM_USER_ID);
        String role = claims.get(CLAIM_ROLE, String.class);
        if (userIdValue == null || role == null) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "登录凭证无效");
        }
        return new LoginUser(Long.valueOf(String.valueOf(userIdValue)), role);
    }

    /**
     * 解析 admin-token，失败统一抛出 401 业务异常
     */
    public AdminTokenClaims parseAdminToken(String token) {
        Claims claims = parseClaims(token);
        if (!SCOPE_ADMIN.equals(claims.get(CLAIM_SCOPE, String.class))) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "请使用店长后台账号登录");
        }
        Object adminIdValue = claims.get(CLAIM_ADMIN_ID);
        String username = claims.get(CLAIM_USERNAME, String.class);
        String tokenId = claims.getId();
        if (adminIdValue == null || username == null || tokenId == null) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "登录凭证无效");
        }
        return new AdminTokenClaims(
                Long.valueOf(String.valueOf(adminIdValue)),
                username,
                tokenId,
                toLocalDateTime(claims.getExpiration()));
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "登录已失效，请重新登录");
        }
    }

    private LocalDateTime toLocalDateTime(Date date) {
        return Instant.ofEpochMilli(date.getTime())
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }
}

package com.coffee.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * admin-token Redis 白名单
 * JWT 本身无法主动失效，退出登录与后台禁用账号时需要删除白名单记录
 */
@Component
@RequiredArgsConstructor
public class AdminTokenStore {

    private static final String KEY_PREFIX = "coffee:admin:token:";

    private final StringRedisTemplate redisTemplate;

    public void save(String tokenId, Long adminId, Duration ttl) {
        redisTemplate.opsForValue().set(KEY_PREFIX + tokenId, String.valueOf(adminId), ttl);
    }

    public boolean isActive(String tokenId, Long adminId) {
        String cachedAdminId = redisTemplate.opsForValue().get(KEY_PREFIX + tokenId);
        return cachedAdminId != null && cachedAdminId.equals(String.valueOf(adminId));
    }

    public void remove(String tokenId) {
        redisTemplate.delete(KEY_PREFIX + tokenId);
    }
}

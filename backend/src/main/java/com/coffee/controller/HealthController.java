package com.coffee.controller;

import com.coffee.common.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查：供 Docker / Nginx / 云托管探活使用，无需登录
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthController {

    /** 依赖探测超时（秒） */
    private static final int PROBE_TIMEOUT_SECONDS = 1;

    private final DataSource dataSource;
    private final StringRedisTemplate stringRedisTemplate;

    @GetMapping("/health")
    public ResponseEntity<Result<Map<String, Object>>> health() {
        boolean databaseUp = checkDatabase();
        boolean redisUp = checkRedis();
        boolean healthy = databaseUp && redisUp;

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("status", healthy ? "UP" : "DOWN");
        detail.put("database", databaseUp ? "UP" : "DOWN");
        detail.put("redis", redisUp ? "UP" : "DOWN");
        detail.put("time", LocalDateTime.now().toString());

        if (healthy) {
            return ResponseEntity.ok(Result.ok(detail));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new Result<>(HttpStatus.SERVICE_UNAVAILABLE.value(), "服务依赖不可用", detail));
    }

    private boolean checkDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(PROBE_TIMEOUT_SECONDS);
        } catch (Exception exception) {
            log.warn("健康检查：数据库不可用 {}", exception.getMessage());
            return false;
        }
    }

    private boolean checkRedis() {
        RedisConnectionFactory connectionFactory = stringRedisTemplate.getConnectionFactory();
        if (connectionFactory == null) {
            return false;
        }
        try (RedisConnection connection = connectionFactory.getConnection()) {
            return "PONG".equalsIgnoreCase(connection.ping());
        } catch (Exception exception) {
            log.warn("健康检查：Redis 不可用 {}", exception.getMessage());
            return false;
        }
    }
}

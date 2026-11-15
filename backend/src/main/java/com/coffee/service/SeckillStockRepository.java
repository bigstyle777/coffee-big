package com.coffee.service;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 秒杀库存 Redis 操作
 * 库存与用户限购的原子扣减/回滚全部通过 Lua 完成
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillStockRepository {

    /** 未预热 */
    public static final long NOT_PREHEATED = -1L;
    /** 超过每人限购 */
    public static final long LIMIT_EXCEEDED = -2L;
    /** 已售罄 */
    public static final long SOLD_OUT = -3L;

    private static final String STOCK_KEY_PREFIX = "coffee:seckill:stock:";
    private static final String USER_KEY_PREFIX = "coffee:seckill:user:";
    private static final String REQUEST_LOCK_PREFIX = "coffee:seckill:req:";
    private static final long MIN_TTL_SECONDS = 60L;

    /**
     * 预热脚本：库存只降不升，避免活动进行中重复预热把可售数量放大
     */
    private static final DefaultRedisScript<Long> PREHEAT_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            local target = tonumber(ARGV[1])
            if not current then
                redis.call('SET', KEYS[1], target, 'EX', ARGV[2])
                return target
            end
            local nextValue = math.min(tonumber(current), target)
            redis.call('SET', KEYS[1], nextValue, 'EX', ARGV[2])
            return nextValue
            """, Long.class);

    /**
     * 扣减脚本：库存、用户限购在同一段 Lua 内原子完成
     * 返回 -1 未预热 / -2 超限购 / -3 售罄 / >=0 剩余库存
     */
    private static final DefaultRedisScript<Long> DEDUCT_SCRIPT = new DefaultRedisScript<>("""
            local stock = redis.call('GET', KEYS[1])
            if not stock then
                return -1
            end
            local quantity = tonumber(ARGV[1])
            if tonumber(stock) < quantity then
                return -3
            end
            local bought = tonumber(redis.call('GET', KEYS[2]) or '0')
            if bought + quantity > tonumber(ARGV[2]) then
                return -2
            end
            redis.call('DECRBY', KEYS[1], quantity)
            redis.call('INCRBY', KEYS[2], quantity)
            redis.call('EXPIRE', KEYS[2], ARGV[3])
            return tonumber(stock) - quantity
            """, Long.class);

    /**
     * 回滚脚本：恢复库存与用户已购数量
     */
    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT = new DefaultRedisScript<>("""
            redis.call('INCRBY', KEYS[1], ARGV[1])
            redis.call('EXPIRE', KEYS[1], ARGV[2])
            local bought = tonumber(redis.call('GET', KEYS[2]) or '0')
            local remain = bought - tonumber(ARGV[1])
            if remain <= 0 then
                redis.call('DEL', KEYS[2])
            else
                redis.call('SET', KEYS[2], remain, 'KEEPTTL')
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * 预热或更新活动库存，返回预热后的剩余库存
     */
    public Long preheat(Long activityProductId, int targetStock, Duration ttl) {
        Long result = redisTemplate.execute(
                PREHEAT_SCRIPT,
                List.of(stockKey(activityProductId)),
                String.valueOf(Math.max(targetStock, 0)),
                String.valueOf(ttlSeconds(ttl)));
        return result;
    }

    /**
     * 查询 Redis 剩余库存，未预热返回 null
     */
    public Integer getStock(Long activityProductId) {
        String value = redisTemplate.opsForValue().get(stockKey(activityProductId));
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            log.warn("秒杀库存值格式异常 activityProductId={}, value={}", activityProductId, value);
            return null;
        }
    }

    /**
     * 原子扣减活动库存与用户限购
     */
    public long deduct(Long activityProductId, Long userId, int quantity,
                       int limitPerUser, Duration ttl) {
        Long result = redisTemplate.execute(
                DEDUCT_SCRIPT,
                List.of(stockKey(activityProductId), userKey(activityProductId, userId)),
                String.valueOf(quantity),
                String.valueOf(limitPerUser),
                String.valueOf(ttlSeconds(ttl)));
        return result == null ? NOT_PREHEATED : result;
    }

    /**
     * 回滚活动库存与用户限购
     */
    public void rollback(Long activityProductId, Long userId, int quantity, Duration ttl) {
        redisTemplate.execute(
                ROLLBACK_SCRIPT,
                List.of(stockKey(activityProductId), userKey(activityProductId, userId)),
                String.valueOf(quantity),
                String.valueOf(ttlSeconds(ttl)));
    }

    /**
     * 获取下单请求锁，防止同一 requestId 并发重复扣库存
     */
    public boolean acquireRequestLock(Long userId, String requestId, Duration ttl) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(requestLockKey(userId, requestId), "1", ttl);
        return Boolean.TRUE.equals(acquired);
    }

    public void releaseRequestLock(Long userId, String requestId) {
        redisTemplate.delete(requestLockKey(userId, requestId));
    }

    /**
     * 清理库存缓存：修改活动商品配置后需要重新预热
     */
    public void clearStock(Long activityProductId) {
        redisTemplate.delete(stockKey(activityProductId));
    }

    private long ttlSeconds(Duration ttl) {
        return Math.max(ttl.getSeconds(), MIN_TTL_SECONDS);
    }

    private String stockKey(Long activityProductId) {
        return STOCK_KEY_PREFIX + activityProductId;
    }

    private String userKey(Long activityProductId, Long userId) {
        return USER_KEY_PREFIX + activityProductId + ":" + userId;
    }

    private String requestLockKey(Long userId, String requestId) {
        return REQUEST_LOCK_PREFIX + userId + ":" + requestId;
    }
}

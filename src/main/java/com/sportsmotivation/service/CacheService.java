package com.sportsmotivation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class CacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public CacheService(RedisTemplate<String, Object> redisTemplate,
                        StringRedisTemplate stringRedisTemplate,
                        ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    // Generic cache operations
    public void set(String key, Object value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl.toSeconds(), TimeUnit.SECONDS);
    }

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public <T> T get(String key, Class<T> type) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) return null;

        if (type.isInstance(value)) {
            return type.cast(value);
        }

        // Handle JSON conversion if needed
        try {
            String json = objectMapper.writeValueAsString(value);
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    public void deletePattern(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    // User-specific cache operations
    public void cacheUserBehavior(Long userId, String behaviorSummary, Duration ttl) {
        String key = "user:behavior:" + userId;
        stringRedisTemplate.opsForValue().set(key, behaviorSummary, ttl);
    }

    public String getUserBehavior(Long userId) {
        String key = "user:behavior:" + userId;
        return stringRedisTemplate.opsForValue().get(key);
    }

    public void clearUserBehavior(Long userId) {
        String key = "user:behavior:" + userId;
        stringRedisTemplate.delete(key);
    }

    // Recommendation cache operations
    public void cacheRecommendations(Long userId, List<Long> videoIds, Duration ttl) {
        String key = "recommendations:" + userId;
        try {
            String json = objectMapper.writeValueAsString(videoIds);
            stringRedisTemplate.opsForValue().set(key, json, ttl);
        } catch (JsonProcessingException e) {
            // Log error, but don't fail the operation
        }
    }

    @SuppressWarnings("unchecked")
    public List<Long> getRecommendations(Long userId) {
        String key = "recommendations:" + userId;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null) return null;

        try {
            return objectMapper.readValue(json, List.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public void clearRecommendations(Long userId) {
        String key = "recommendations:" + userId;
        stringRedisTemplate.delete(key);
    }

    // Video metadata cache operations
    public void cacheVideoMetadata(String youtubeId, Object videoData, Duration ttl) {
        String key = "video:metadata:" + youtubeId;
        set(key, videoData, ttl);
    }

    public <T> T getVideoMetadata(String youtubeId, Class<T> type) {
        String key = "video:metadata:" + youtubeId;
        return get(key, type);
    }

    // Trending content cache
    public void cacheTrendingVideos(String category, List<Long> videoIds, Duration ttl) {
        String key = "trending:" + category;
        try {
            String json = objectMapper.writeValueAsString(videoIds);
            stringRedisTemplate.opsForValue().set(key, json, ttl);
        } catch (JsonProcessingException e) {
            // Log error
        }
    }

    @SuppressWarnings("unchecked")
    public List<Long> getTrendingVideos(String category) {
        String key = "trending:" + category;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null) return null;

        try {
            return objectMapper.readValue(json, List.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    // Cache statistics and monitoring
    public long getCacheSize() {
        return redisTemplate.getConnectionFactory().getConnection().dbSize();
    }

    public void clearAllCache() {
        redisTemplate.getConnectionFactory().getConnection().flushDb();
    }

    // Cache warming methods
    public void warmUserCache(Long userId, String behaviorSummary) {
        cacheUserBehavior(userId, behaviorSummary, Duration.ofHours(1));
    }

    public void warmVideoCache(String youtubeId, Object videoData) {
        cacheVideoMetadata(youtubeId, videoData, Duration.ofHours(24));
    }
}
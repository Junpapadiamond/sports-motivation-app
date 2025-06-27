package com.sportsmotivation.service;

import com.sportsmotivation.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.secret:default-secret-key}")
    private String jwtSecret;

    @Value("${jwt.expiration.seconds:3600}")
    private long accessTokenExpirationSeconds;

    @Value("${jwt.refresh.expiration.seconds:604800}") // 7 days
    private long refreshTokenExpirationSeconds;

    @Value("${jwt.issuer:sports-motivation-app}")
    private String issuer;

    // Rate limiting
    @Value("${auth.rate-limit.max-attempts:5}")
    private int maxLoginAttempts;

    @Value("${auth.rate-limit.window-minutes:15}")
    private int rateLimitWindowMinutes;

    @Autowired
    public AuthService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generate access token with user claims and permissions
     */
    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpirationSeconds * 1000);

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername());
        claims.put("email", user.getEmail());
        claims.put("role", determineUserRole(user));
        claims.put("preferences", user.getSportsPreferences());
        claims.put("tokenType", "ACCESS");

        return Jwts.builder()
                .claims(claims)
                .subject(user.getId().toString())
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Generate refresh token for token rotation
     */
    public String generateRefreshToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshTokenExpirationSeconds * 1000);
        String tokenId = UUID.randomUUID().toString();

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("tokenType", "REFRESH");
        claims.put("tokenId", tokenId);

        String refreshToken = Jwts.builder()
                .claims(claims)
                .subject(user.getId().toString())
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();

        // Store refresh token in Redis for validation
        String redisKey = "refresh_token:" + user.getId() + ":" + tokenId;
        redisTemplate.opsForValue().set(redisKey, "valid",
                Duration.ofSeconds(refreshTokenExpirationSeconds));

        return refreshToken;
    }

    /**
     * Refresh access token using valid refresh token
     */
    public RefreshTokenResponse refreshAccessToken(String refreshToken) {
        try {
            Claims claims = validateAndParseClaims(refreshToken);

            if (!"REFRESH".equals(claims.get("tokenType"))) {
                throw new IllegalArgumentException("Invalid token type for refresh");
            }

            Long userId = Long.parseLong(claims.getSubject());
            String tokenId = (String) claims.get("tokenId");

            // Check if refresh token exists in Redis
            String redisKey = "refresh_token:" + userId + ":" + tokenId;
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) {
                throw new IllegalArgumentException("Refresh token has been revoked");
            }

            // Token is valid, but we need User object to generate new access token
            // This requires UserService dependency, but we'll return the info needed
            return new RefreshTokenResponse(userId, tokenId, true);

        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid refresh token: " + e.getMessage());
        }
    }

    /**
     * Validate JWT token and return whether it is valid
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = validateAndParseClaims(token);

            // Check if token is blacklisted
            if (isTokenBlacklisted(token)) {
                return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extract user id from JWT token
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = validateAndParseClaims(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * Extract all claims from token
     */
    public Claims getClaimsFromToken(String token) {
        return validateAndParseClaims(token);
    }

    /**
     * Logout user by blacklisting their tokens
     */
    public void logout(String accessToken, String refreshToken) {
        try {
            // Blacklist access token
            blacklistToken(accessToken);

            // Revoke refresh token
            if (refreshToken != null) {
                revokeRefreshToken(refreshToken);
            }

        } catch (Exception e) {
            // Log error but don't fail logout
        }
    }

    /**
     * Blacklist a token (for logout)
     */
    public void blacklistToken(String token) {
        try {
            Claims claims = validateAndParseClaims(token);
            Date expiration = claims.getExpiration();

            if (expiration.after(new Date())) {
                String tokenHash = Integer.toString(token.hashCode());
                long ttl = (expiration.getTime() - System.currentTimeMillis()) / 1000;

                redisTemplate.opsForValue().set(
                        "blacklisted_token:" + tokenHash,
                        "true",
                        Duration.ofSeconds(ttl)
                );
            }
        } catch (Exception e) {
            // Token is invalid anyway, so blacklisting is unnecessary
        }
    }

    /**
     * Check if token is blacklisted
     */
    public boolean isTokenBlacklisted(String token) {
        String tokenHash = Integer.toString(token.hashCode());
        return Boolean.TRUE.equals(redisTemplate.hasKey("blacklisted_token:" + tokenHash));
    }

    /**
     * Rate limiting for login attempts
     */
    public boolean isRateLimited(String identifier) {
        String key = "login_attempts:" + identifier;
        String attempts = redisTemplate.opsForValue().get(key);

        if (attempts == null) {
            return false;
        }

        return Integer.parseInt(attempts) >= maxLoginAttempts;
    }

    /**
     * Record failed login attempt
     */
    public void recordFailedLoginAttempt(String identifier) {
        String key = "login_attempts:" + identifier;
        String attempts = redisTemplate.opsForValue().get(key);

        if (attempts == null) {
            redisTemplate.opsForValue().set(key, "1", Duration.ofMinutes(rateLimitWindowMinutes));
        } else {
            redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, Duration.ofMinutes(rateLimitWindowMinutes));
        }
    }

    /**
     * Clear failed login attempts (on successful login)
     */
    public void clearFailedLoginAttempts(String identifier) {
        redisTemplate.delete("login_attempts:" + identifier);
    }

    /**
     * Revoke refresh token
     */
    public void revokeRefreshToken(String refreshToken) {
        try {
            Claims claims = validateAndParseClaims(refreshToken);
            Long userId = Long.parseLong(claims.getSubject());
            String tokenId = (String) claims.get("tokenId");

            String redisKey = "refresh_token:" + userId + ":" + tokenId;
            redisTemplate.delete(redisKey);

        } catch (Exception e) {
            // Token is invalid, so revocation is unnecessary
        }
    }

    /**
     * Revoke all refresh tokens for a user (for security purposes)
     */
    public void revokeAllRefreshTokens(Long userId) {
        String pattern = "refresh_token:" + userId + ":*";
        redisTemplate.delete(redisTemplate.keys(pattern));
    }

    /**
     * Get token expiration time
     */
    public Date getTokenExpiration(String token) {
        Claims claims = validateAndParseClaims(token);
        return claims.getExpiration();
    }

    /**
     * Check if token is about to expire (within 5 minutes)
     */
    public boolean isTokenNearExpiry(String token) {
        try {
            Date expiration = getTokenExpiration(token);
            long timeUntilExpiry = expiration.getTime() - System.currentTimeMillis();
            return timeUntilExpiry < (5 * 60 * 1000); // 5 minutes in milliseconds
        } catch (Exception e) {
            return true; // Treat invalid tokens as expired
        }
    }

    // Private helper methods

    private Claims validateAndParseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private String determineUserRole(User user) {
        // Simple role determination - can be expanded
        if (user.getEmail().contains("admin")) {
            return "ADMIN";
        }

        // Check if user has been active for more than 30 days
        if (user.getCreatedAt().isBefore(LocalDateTime.now().minusDays(30))) {
            return "PREMIUM_USER";
        }

        return "USER";
    }

    // Response classes for token operations

    public static class RefreshTokenResponse {
        private final Long userId;
        private final String tokenId;
        private final boolean valid;

        public RefreshTokenResponse(Long userId, String tokenId, boolean valid) {
            this.userId = userId;
            this.tokenId = tokenId;
            this.valid = valid;
        }

        public Long getUserId() { return userId; }
        public String getTokenId() { return tokenId; }
        public boolean isValid() { return valid; }
    }

    public static class TokenPair {
        private final String accessToken;
        private final String refreshToken;

        public TokenPair(String accessToken, String refreshToken) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }

        public String getAccessToken() { return accessToken; }
        public String getRefreshToken() { return refreshToken; }
    }
}
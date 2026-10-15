package com.nl2sql.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

/**
 * JWT 工具类 - 生成/解析 token
 */
@Component
public class JwtUtil {

    @Value("${nl2sql.jwt.secret}")
    private String secret;

    @Value("${nl2sql.jwt.expiration-seconds}")
    private long expirationSeconds;

    private SecretKey key;

    @PostConstruct
    void init() {
        byte[] bytes = Base64.getDecoder().decode(secret);
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public String generate(Long userId, String username) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expirationSeconds * 1000L);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long extractUserId(String token) {
        return Long.parseLong(parse(token).getSubject());
    }
}

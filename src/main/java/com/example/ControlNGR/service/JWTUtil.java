package com.example.ControlNGR.service;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;

@Component
public class JWTUtil {
    private static final Logger logger = LoggerFactory.getLogger(JWTUtil.class);

    private final SecretKey key;
    private final long expirationTime;

    public JWTUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration:36000}") long expirationSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationTime = expirationSeconds * 1000; // convertir a milisegundos
    }

    public String generateToken(String username, String rol) {
        return Jwts.builder()
                .setSubject(username)
                .claim("rol", rol)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getSubject();
        } catch (Exception e) {
            logger.error("Error extrayendo username del token: {}", e.getMessage());
            return null;
        }
    }

    public String extractRol(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .get("rol", String.class);
        } catch (Exception e) {
            logger.error("Error extrayendo rol del token: {}", e.getMessage());
            return null;
        }
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException e) {
            logger.error("Token expirado: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.error("Token no soportado: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            logger.error("Token malformado: {}", e.getMessage());
        } catch (SecurityException e) {
            logger.error("Error de seguridad con el token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("Token vacío o nulo: {}", e.getMessage());
        }
        return false;
    }
}
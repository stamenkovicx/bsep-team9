package com.bsep.pki_system.jwt;

import com.bsep.pki_system.model.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpirationMs;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(User user) {
        return Jwts.builder()
                .setSubject(user.getEmail())
                .claim("userId", user.getId())
                .claim("role", user.getRole().name())
                .claim("is2FAEnabled", user.getIs2faEnabled())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    public Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String generateTemporaryToken(User user) {
        // kraći rok važenja, npr. 10 minuta
        long temporaryExpirationMs = 10 * 60 * 1000; // 10 minuta

        return Jwts.builder()
                .setSubject(user.getEmail())
                .claim("userId", user.getId())
                .claim("role", user.getRole().name()) // možeš staviti i "TEMPORARY"
                .claim("temporary", true) // oznaka da je token privremen
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + temporaryExpirationMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String getEmailFromTemporaryToken(String token) {
        try {
            Claims claims = getClaims(token);

            // Provera da li je token zaista privremen
            Boolean temporary = claims.get("temporary", Boolean.class);
            if (temporary == null || !temporary) {
                return null; // nije privremeni token
            }

            return claims.getSubject(); // subject je email
        } catch (JwtException | IllegalArgumentException e) {
            return null; // token ne važi ili je neispravan
        }
    }
}

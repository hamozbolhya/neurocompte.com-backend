package com.pacioli.core.utils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
@Component
public class JwtUtil {
    @Value("${jwt.secret}")
    private String SECRET_KEY;

    @Value("${jwt.expiration}")
    private long EXPIRATION_TIME;

    public String generateToken(String username, String email, Long cabinetId, String cabinetName, List<String> roles, Boolean active) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);
        claims.put("cabinetId", cabinetId);
        claims.put("cabinetName", cabinetName);
        claims.put("active", active);
        claims.put("roles", roles);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(SignatureAlgorithm.HS256, SECRET_KEY)
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    public String extractEmail(String token) {
        return (String) extractClaims(token).get("email");
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .setSigningKey(SECRET_KEY)
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean isTokenExpired(String token) {
        return extractClaims(token).getExpiration().before(new Date());
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        return (List<String>) extractClaims(token).get("roles");
    }

    public Long extractCabinetId(String token) {
        Object cabinetId = extractClaims(token).get("cabinetId");
        if (cabinetId instanceof Number) {
            return ((Number) cabinetId).longValue();
        }
        return null;
    }

    public String extractCabinetName(String token) {
        return (String) extractClaims(token).get("cabinetName");
    }

    public Boolean extractActive(String token) {
        return (Boolean) extractClaims(token).get("active");
    }
}

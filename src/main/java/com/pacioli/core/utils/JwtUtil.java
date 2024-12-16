package com.pacioli.core.utils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
@Component
public class JwtUtil {

    private String SECRET_KEY = "c1194df3f35adf875f7dc30d89f366492f184a4c0e84c32ce2c98eeb5574a5dd"; // Change this to a strong secret key
    private long EXPIRATION_TIME = 1000000; // 10 minutes

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

    public boolean validateToken(String token, String username) {
        String extractedUsername = extractUsername(token);
        return (extractedUsername.equals(username) && !isTokenExpired(token));
    }
}

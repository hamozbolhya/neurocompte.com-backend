package com.pacioli.core.filters;

import com.pacioli.core.utils.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class JwtRequestFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        final String authorizationHeader = request.getHeader("Authorization");

        String username = null;
        String jwt = null;

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7);
            try {
                username = jwtUtil.extractUsername(jwt);
                log.info("JWT Token extracted username: {}", username);
            } catch (Exception e) {
                log.warn("Unable to extract username from JWT: {}", e.getMessage());
            }
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                // Validate token
                if (!jwtUtil.isTokenExpired(jwt)) {

                    // Extract roles directly from JWT token - DON'T load from database
                    List<String> roles = jwtUtil.extractRoles(jwt);
                    log.info("Extracted roles from JWT: {}", roles);

                    // Convert roles to Spring Security authorities
                    Collection<GrantedAuthority> authorities = roles.stream()
                            .map(SimpleGrantedAuthority::new)  // Creates authorities like "PACIOLI"
                            .collect(Collectors.toList());

                    log.info("Created authorities: {}", authorities.stream()
                            .map(GrantedAuthority::getAuthority)
                            .collect(Collectors.toList()));

                    // Create UserDetails with JWT-based authorities (not from database)
                    UserDetails userDetails = User.builder()
                            .username(username)
                            .password("") // Password not needed for JWT authentication
                            .authorities(authorities)
                            .build();

                    // Create authentication token
                    UsernamePasswordAuthenticationToken authenticationToken =
                            new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // Set authentication in security context
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                    log.info("Successfully set authentication for user: {} with authorities: {}",
                            username, authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList()));

                } else {
                    log.warn("JWT Token has expired for user: {}", username);
                }
            } catch (Exception e) {
                log.error("Error processing JWT token for user {}: {}", username, e.getMessage());
            }
        }

        chain.doFilter(request, response);
    }
}
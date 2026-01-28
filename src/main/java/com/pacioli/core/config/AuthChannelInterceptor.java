package com.pacioli.core.config;

import com.pacioli.core.utils.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

@Component
public class AuthChannelInterceptor implements ChannelInterceptor {
    private static final Logger log = LoggerFactory.getLogger(AuthChannelInterceptor.class);

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null &&
                (StompCommand.CONNECT.equals(accessor.getCommand()) ||
                        StompCommand.SUBSCRIBE.equals(accessor.getCommand()) ||
                        StompCommand.MESSAGE.equals(accessor.getCommand()))) {

            String token = accessor.getFirstNativeHeader("Authorization");

            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
                try {
                    String username = jwtUtil.extractUsername(token);
                    List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(username, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    accessor.setUser(auth);
                } catch (ExpiredJwtException e) {
                    log.warn("Expired JWT in WebSocket message: {}", e.getMessage());
                    // Rejet du message avec exception claire côté client
                    throw new org.springframework.messaging.MessageDeliveryException("JWT expired");
                } catch (Exception e) {
                    log.warn("Invalid JWT in WebSocket message: {}", e.getMessage());
                    throw new org.springframework.messaging.MessageDeliveryException("JWT invalid");
                }
            } else {
                log.warn("No Authorization header in WebSocket request");
                throw new org.springframework.messaging.MessageDeliveryException("Missing Authorization header");
            }
        }

        return message;
    }
}
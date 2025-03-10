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

        if (StompCommand.CONNECT.equals(accessor.getCommand()) ||
                StompCommand.SUBSCRIBE.equals(accessor.getCommand()) ||
                StompCommand.MESSAGE.equals(accessor.getCommand())) {

            String token = accessor.getFirstNativeHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
                try {
                    String username = jwtUtil.extractUsername(token);
                    List<GrantedAuthority> authorities = Arrays.asList(new SimpleGrantedAuthority("ROLE_USER"));
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(username, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    accessor.setUser(auth);
                } catch (ExpiredJwtException e) {
                    log.warn("Expired JWT token in WebSocket connection: {}", e.getMessage());
                    // Two options here:

                    // Option 1: Allow connection but mark as unauthenticated (anonymous)
                    // This lets the connection proceed but you can check auth status in your handlers

                    // Option 2: Let the exception propagate - client will receive an error
                    // and should refresh the token and reconnect
                    throw e; // Comment this line for Option 1
                }
            }
        }
        return message;
    }
}
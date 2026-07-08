package com.portable.microservices.ms_inventory.shared.infrastructure.websocket;

import java.util.List;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import com.portable.microservices.ms_inventory.shared.infrastructure.security.jwt.JwtUtil;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("Conexión STOMP sin token JWT");
                throw new IllegalArgumentException("Token JWT requerido en header Authorization");
            }

            try {
                Claims claims = jwtUtil.validateToken(authHeader.substring(7));
                String username = claims.getSubject();
                String role = claims.get("role", String.class);

                var auth = new UsernamePasswordAuthenticationToken(
                        username, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                accessor.setUser(auth);
                log.debug("STOMP CONNECT autenticado: usuario={}, role={}", username, role);
            } catch (JwtException e) {
                String reason = jwtUtil.getErrorReason(e);
                log.warn("Conexión STOMP con token inválido: {}", reason);
                throw new IllegalArgumentException("Token JWT inválido: " + reason);
            }
        }

        return message;
    }
}

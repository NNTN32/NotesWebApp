package com.example.notesWeb.security;

import com.example.notesWeb.config.jwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class StompSecurityInterceptor implements ChannelInterceptor {
    private final jwtProvider tokens;
    private static final String EXPIRES_AT = "notes.accessExpiresAt";
    private static final Set<String> SUBSCRIPTIONS = Set.of(
            "/user/queue/note-updates", "/user/queue/reminder");

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null) throw new AccessDeniedException("Invalid STOMP frame");
        StompCommand command = headers.getCommand();
        // Disconnect and heartbeat frames cannot publish or subscribe.
        if (command == StompCommand.DISCONNECT || command == null) return message;
        if (command == StompCommand.CONNECT) {
            String authorization = headers.getFirstNativeHeader("Authorization");
            if (authorization == null || !authorization.startsWith("Bearer ")
                    || !tokens.validateToken(authorization.substring(7))) {
                throw new AccessDeniedException("Access token required");
            }
            String token = authorization.substring(7);
            headers.setUser(new UsernamePasswordAuthenticationToken(tokens.getUserFromJwt(token), null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + tokens.getRoleFromJwt(token)))));
            if (headers.getSessionAttributes() == null) throw new AccessDeniedException("Missing session");
            headers.getSessionAttributes().put(EXPIRES_AT,
                    tokens.extractAllClaims(token).getExpiration().getTime());
            headers.removeNativeHeader("Authorization");
            return message;
        }
        Object expiresAt = headers.getSessionAttributes() == null ? null
                : headers.getSessionAttributes().get(EXPIRES_AT);
        if (headers.getUser() == null || !(expiresAt instanceof Long expiry)
                || expiry <= System.currentTimeMillis()) {
            throw new AccessDeniedException("Authenticated session required");
        }
        if (command == StompCommand.SUBSCRIBE && !SUBSCRIPTIONS.contains(headers.getDestination())) {
            throw new AccessDeniedException("Subscription denied");
        }
        // This app exposes notifications only; clients must never impersonate publishers.
        if (command == StompCommand.SEND) throw new AccessDeniedException("Publishing denied");
        return message;
    }
}

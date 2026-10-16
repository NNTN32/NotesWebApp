package com.example.notesWeb.auth.security;


import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import io.jsonwebtoken.Jwts;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StompSecurityTest {
    final jwtProvider tokens = mock(jwtProvider.class);
    final StompSecurityInterceptor interceptor = new StompSecurityInterceptor(tokens);
    StompHeaderAccessor headers(StompCommand command, Map<String, Object> session) {
        var h = StompHeaderAccessor.create(command); h.setSessionId("session-1");
        h.setSessionAttributes(session); h.setLeaveMutable(true); return h;
    }
    Message<byte[]> message(StompHeaderAccessor h) { return MessageBuilder.createMessage(new byte[0], h.getMessageHeaders()); }
    @Test void connectRejectsMissingOrRotationToken() {
        var h = headers(StompCommand.CONNECT, new HashMap<>());
        assertThatThrownBy(() -> interceptor.preSend(message(h), null)).isInstanceOf(AccessDeniedException.class);
        h.setNativeHeader("Authorization", "Bearer rotation-token");
        assertThatThrownBy(() -> interceptor.preSend(message(h), null)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void authenticatedUserCanSubscribeOnlyToOwnNotificationDestinations() {
        when(tokens.validateToken("access")).thenReturn(true);
        when(tokens.getUserFromJwt("access")).thenReturn("alice");
        when(tokens.getRoleFromJwt("access")).thenReturn("USER");
        when(tokens.extractAllClaims("access")).thenReturn(Jwts.claims().expiration(new Date(System.currentTimeMillis()+60000)).build());
        Map<String,Object> session = new HashMap<>();
        var connect = headers(StompCommand.CONNECT, session);
        connect.setNativeHeader("Authorization", "Bearer access");
        interceptor.preSend(message(connect), null);
        assertThat(connect.getUser().getName()).isEqualTo("alice");
        assertThat(connect.getFirstNativeHeader("Authorization")).isNull();
        var sub = headers(StompCommand.SUBSCRIBE, session); sub.setUser(connect.getUser());
        for (String allowed : List.of("/user/queue/reminder", "/user/queue/note-updates")) {
            sub.setDestination(allowed); interceptor.preSend(message(sub), null);
        }
        for (String forbidden : List.of("/queue/reminder", "/topic/test", "/user/bob/queue/reminder", "/user/queue/*")) {
            sub.setDestination(forbidden);
            assertThatThrownBy(() -> interceptor.preSend(message(sub), null)).isInstanceOf(AccessDeniedException.class);
        }
        var send = headers(StompCommand.SEND, session); send.setUser(connect.getUser()); send.setDestination("/queue/reminder");
        assertThatThrownBy(() -> interceptor.preSend(message(send), null)).isInstanceOf(AccessDeniedException.class);
        session.put("notes.accessExpiresAt", 0L); sub.setDestination("/user/queue/reminder");
        assertThatThrownBy(() -> interceptor.preSend(message(sub), null)).isInstanceOf(AccessDeniedException.class);
    }
}

package com.example.notesWeb.security;

import com.example.notesWeb.config.jwtAuthenticationFilter;
import com.example.notesWeb.config.jwtProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception{
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, jwtProvider jwtProvider ) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {
                })
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/register","/api/auth/login","/api/auth/login/result/{sessionId}",
                                "/notes/creates",
                                "/notes/listNotes/{userID}",
                                "/notes/{noteID}",
                                "/notes/delete/{noteID}",
                                "/notes/update/{noteID}",
                                "/media/uploads/{postID}",
                                "/media/delete/{mediaID}",
                                "/todo/createList",
                                "/todo/update/{todoID}",
                                "/todo/listUser/{userId}",
                                "/todo/delete/{idList}",
                                "/todo/listUpdate/{idList}",
                                "/reminder/set-time/{idListTodo}",
                                "/ws/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new jwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}

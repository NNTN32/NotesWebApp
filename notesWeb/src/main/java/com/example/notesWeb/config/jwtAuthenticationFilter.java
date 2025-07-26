package com.example.notesWeb.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class jwtAuthenticationFilter extends OncePerRequestFilter {
    private final jwtProvider jwtProvider;

    public jwtAuthenticationFilter(jwtProvider jwtProvider){
        this.jwtProvider = jwtProvider;
    }

    private String getJwtFromRequest(HttpServletRequest request){
        String bearerToken = request.getHeader("Authorization");
        if(StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")){
            return bearerToken.substring(7);
        }
        return null;
    }

    //Logic handle token
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException{
        String token = getJwtFromRequest(request);

        if(StringUtils.hasText(token) && jwtProvider.validateToken(token)){
            String username = jwtProvider.getUserFromJwt(token);
            String role = jwtProvider.extractClaim(token, claims -> claims.get("role", String.class));

            //Create Spring Security Authentication Object
            GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);

            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, null, List.of(authority));

            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        }

        filterChain.doFilter(request, response);
    }
}

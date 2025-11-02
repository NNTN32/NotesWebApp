package com.example.notesWeb.config;

import com.example.notesWeb.model.Role;
import com.example.notesWeb.model.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

@Component
public class jwtProvider {
    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    //Create secret key for jwt
    private SecretKey getSignKey(){
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    //Generate Token
    public String generateToken(User user){
        Date now = new Date();
        //Setting time token expired
        Date expiredDate = new Date(now.getTime() + jwtExpiration);

        //return role user + token byte
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("Role:", user.getRole().name())
                .issuedAt(now)
                .expiration(expiredDate)
                .signWith(getSignKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    //Refresh token
    public String refreshToken(String oldToken) {
        if(!validateToken(oldToken)) {
            throw new IllegalArgumentException("Cannot refresh invalid or expired token");
        }

        String userName = getUserFromJwt(oldToken);
        String role = getRoleFromJwt(oldToken);

        User user = new User();
        user.setUsername(userName);
        user.setRole(Role.valueOf(role));

        return generateToken(user);
    }

    //Claim token type "String"
    public Claims extractAllClaims(String token){
        try{
            JwtParser parser = Jwts.parser()
                    .setSigningKey(getSignKey())
                    .build();
            return parser.parseClaimsJws(token).getBody();
        }catch (Exception ex){
            throw new IllegalArgumentException("Invalid Token");
        }
    }

    //Claim object through on jwt
    public <T> T extractClaim(String token, Function<Claims, T> claimResolve){
        final Claims claims = extractAllClaims(token);
        return claimResolve.apply(claims);
    }

    //Get object "user" to jwt
    public String getUserFromJwt(String token){
        return extractClaim(token, Claims::getSubject);
    }

    //Get object "role" to jwt
    public String getRoleFromJwt(String token) { return extractClaim(token, claims -> claims.get("Role:", String.class));}


    //Check time token validate
    public boolean isTokenExpired(String token){
        final Date expiration = extractClaim(token, Claims::getExpiration);
        return expiration.before(new Date());
    }

    //Check token validate
    public boolean validateToken(String token){
        try{
            String username = getUserFromJwt(token);
            return (username != null && !isTokenExpired(token));
        }catch (Exception e){
            System.out.println("Token validation failed: " + e.getMessage());
            return false;
        }
    }

}

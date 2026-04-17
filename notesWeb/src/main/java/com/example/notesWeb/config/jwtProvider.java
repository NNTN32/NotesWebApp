package com.example.notesWeb.config;

import com.example.notesWeb.model.Role;
import com.example.notesWeb.model.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

@Component
@Slf4j
public class jwtProvider {
    public static final String claim_Role = "role";
    public static final String claim_Type = "type"; //distinction access & refresh
    public static final String claim_Id = "userId";

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Value("${jwt.refresh}")
    private Long jwtRefresh;

    //Create secret key for jwt
    private SecretKey getSignKey(){
        //Base64 decoding for key secrets
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    //Generate access Token
    public String generateToken(User user){
        Date now = new Date();
        //Setting time token expired
        Date expiredDate = new Date(System.currentTimeMillis() + jwtExpiration);

        //return role user + token byte
        return Jwts.builder()
                .subject(user.getUsername())
                .claim(claim_Id, user.getId().toString())
                //Consistent key "role"
                .claim(claim_Role, user.getRole().name())
                .claim(claim_Type, "Access")
                .issuedAt(now)
                .expiration(expiredDate)
                .signWith(getSignKey())
                .compact();
    }

    //generate for rotation secret
    public String generateRSToken (User user) {
        Date expiredRefresh = new Date();
        Date trackingTime = new Date(System.currentTimeMillis() + jwtRefresh);

        return Jwts.builder()
                .subject(user.getUsername())
                .claim(claim_Id, user.getId().toString())
                .claim(claim_Role, user.getRole().name())
                .claim(claim_Type, "Rotation")
                .issuedAt(expiredRefresh)
                .expiration(trackingTime)
                .signWith(getSignKey())
                .compact();
    }

    //Claim token type "String"
    public Claims extractAllClaims(String token){
        try{
            //using getPayload to retrieve Claims directly
            return Jwts.parser()
                    .verifyWith(getSignKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        }catch (ExpiredJwtException e){
            //If token expired, it still could claim to refresh
            return e.getClaims();
        }catch (Exception exception) {
            throw new IllegalArgumentException("Invalid token");
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

    public UUID getUserIdFromJwt(String token) {
        String idString = extractClaim(token, claims -> claims.get(claim_Id, String.class));
        if (idString == null) return null; //Prevent bug NPE
        return UUID.fromString(idString);
    }

    //Get object "role" to jwt
    public String getRoleFromJwt(String token) {
        return extractClaim(token, claims -> claims.get(claim_Role, String.class));
    }


    //Check time token validate
    public boolean isTokenExpired(String token){
        final Date expiration = extractClaim(token, Claims::getExpiration);
        return expiration.before(new Date());
    }

    //Check token validate
    public boolean validateToken(String token){
        try{
            Jwts.parser().verifyWith(getSignKey()).build()
                    .parseSignedClaims(token);
            return true;
        }catch (ExpiredJwtException e){
            log.warn("Token expired");
        }catch (Exception e) {
            log.error("Token invalid");
        }return false;
    }

    public long getRemainingTime(String token) {
        Date expiration = extractClaim(token, Claims::getExpiration);
        return Math.max(0, expiration.getTime() - System.currentTimeMillis());
    }
}

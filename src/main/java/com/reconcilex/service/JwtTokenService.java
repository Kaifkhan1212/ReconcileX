package com.reconcilex.service;

import java.util.Date;

import org.springframework.stereotype.Service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.reconcilex.config.JwtProperties;
import com.reconcilex.entity.AppUserRole;

@Service
public class JwtTokenService {

    public static final String ISSUER = "reconcilex";
    public static final long EXPIRY_SECONDS = 1800L; // 30 minutes

    private final JwtProperties jwtProperties;

    public JwtTokenService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /**
     * Generates a signed HS256 JWT access token with 30-minute expiry.
     */
    public String generateToken(String username, AppUserRole role) {
        return generateToken(username, role, EXPIRY_SECONDS);
    }

    /**
     * Generates a signed HS256 JWT access token with custom validity in seconds.
     */
    public String generateToken(String username, AppUserRole role, long validitySeconds) {
        try {
            long nowMillis = System.currentTimeMillis();
            Date now = new Date(nowMillis);
            Date expiry = new Date(nowMillis + validitySeconds * 1000L);

            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .issuer(ISSUER)
                    .subject(username)
                    .claim("role", role.name())
                    .issueTime(now)
                    .expirationTime(expiry)
                    .build();

            SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
            JWSSigner signer = new MACSigner(jwtProperties.getSecretBytes());
            signedJWT.sign(signer);

            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign JWT access token", e);
        }
    }
}

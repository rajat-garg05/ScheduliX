package com.schedulix.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class JwtService {

    private static final String HEADER_B64 = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private static final Pattern SUB_PATTERN = Pattern.compile("\"sub\":\"([^\"]+)\"");
    private static final Pattern EXP_PATTERN = Pattern.compile("\"exp\":(\\d+)");

    private final byte[] secretBytes;
    private final long accessMinutes;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-minutes:15}") long accessMinutes
    ) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("app.jwt.secret must be at least 32 characters long");
        }
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.accessMinutes = accessMinutes;
    }

    public String createAccessToken(UserAccount user) {
        Instant now = Instant.now();
        long iat = now.getEpochSecond();
        long exp = now.plus(accessMinutes, ChronoUnit.MINUTES).getEpochSecond();

        String payload = "{\"sub\":\"" + user.getId()
                + "\",\"dept\":\"" + user.getDepartmentId()
                + "\",\"role\":\"" + user.getRole().name()
                + "\",\"iat\":" + iat
                + ",\"exp\":" + exp + "}";

        String payloadB64 = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        String signingInput = HEADER_B64 + "." + payloadB64;
        String signatureB64 = sign(signingInput);
        return signingInput + "." + signatureB64;
    }

    public Optional<UUID> parseValidUserId(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }
        if (!HEADER_B64.equals(parts[0])) {
            return Optional.empty();
        }

        String expectedSig = sign(parts[0] + "." + parts[1]);
        boolean signatureMatches = MessageDigest.isEqual(
                expectedSig.getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8)
        );
        if (!signatureMatches) {
            return Optional.empty();
        }

        try {
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Matcher subMatcher = SUB_PATTERN.matcher(payload);
            Matcher expMatcher = EXP_PATTERN.matcher(payload);
            if (!subMatcher.find() || !expMatcher.find()) {
                return Optional.empty();
            }

            long exp = Long.parseLong(expMatcher.group(1));
            if (Instant.now().getEpochSecond() >= exp) {
                return Optional.empty();
            }

            return Optional.of(UUID.fromString(subMatcher.group(1)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
            byte[] signature = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Could not sign access token", ex);
        }
    }
}
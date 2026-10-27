package com.echogallery.experiment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ExplorationPreviewTokens {
    private final ObjectMapper mapper;
    private final byte[] secret;
    public ExplorationPreviewTokens(ObjectMapper mapper, @Value("${jwt.secret}") String secret) {
        this.mapper = mapper; this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }
    public String encode(Object value) {
        String body = Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(value));
        return body + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(body));
    }
    public <T> T decode(String token, Class<T> type) {
        try {
            if (token.length() > 1000000) throw new IllegalArgumentException();
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2 || !MessageDigest.isEqual(sign(parts[0]), Base64.getUrlDecoder().decode(parts[1])))
                throw new IllegalArgumentException();
            return mapper.readValue(Base64.getUrlDecoder().decode(parts[0]), type);
        } catch (Exception e) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "整理預覽無效，請重新預覽");
        }
    }
    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}

package de.grindpvp.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class AdminCodeVerifier {
    private final byte[] configuredCode;

    public AdminCodeVerifier(@Value("${grindpvp.admin-code}") String configuredCode) {
        this.configuredCode = configuredCode.getBytes(StandardCharsets.UTF_8);
    }

    public void verify(String suppliedCode) {
        if (suppliedCode == null || !MessageDigest.isEqual(configuredCode,
                suppliedCode.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid admin code.");
        }
    }
}

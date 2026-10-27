package de.grindpvp.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class ServerTokenVerifier {
    private final byte[] configuredToken;

    public ServerTokenVerifier(@Value("${grindpvp.server-token}") String configuredToken) {
        this.configuredToken = configuredToken.getBytes(StandardCharsets.UTF_8);
    }

    public void verify(String suppliedToken) {
        if (configuredToken.length == 0 || suppliedToken == null
                || !MessageDigest.isEqual(configuredToken, suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid server token.");
        }
    }
}

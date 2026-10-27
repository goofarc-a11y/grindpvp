package de.grindpvp.backend.api;

import de.grindpvp.backend.service.GrindPvPService;
import de.grindpvp.backend.service.ProfileView;
import de.grindpvp.backend.service.ServerTokenVerifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/server")
public class ServerController {
    private final GrindPvPService service;
    private final ServerTokenVerifier tokenVerifier;

    public ServerController(GrindPvPService service, ServerTokenVerifier tokenVerifier) {
        this.service = service;
        this.tokenVerifier = tokenVerifier;
    }

    @PostMapping("/players")
    public ProfileView registerPlayer(
            @RequestHeader("X-Server-Token") String serverToken,
            @RequestBody PlayerRegistration registration
    ) {
        tokenVerifier.verify(serverToken);
        return service.registerServerPlayer(registration.id(), registration.username(), registration.hasMod());
    }

    public record PlayerRegistration(UUID id, String username, boolean hasMod) {
    }
}

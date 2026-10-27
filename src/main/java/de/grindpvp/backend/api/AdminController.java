package de.grindpvp.backend.api;

import de.grindpvp.backend.model.Role;
import de.grindpvp.backend.service.AdminCodeVerifier;
import de.grindpvp.backend.service.GrindPvPService;
import de.grindpvp.backend.service.GrindPvPService.ResultSubmission;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final GrindPvPService service;
    private final AdminCodeVerifier codeVerifier;

    public AdminController(GrindPvPService service, AdminCodeVerifier codeVerifier) {
        this.service = service;
        this.codeVerifier = codeVerifier;
    }

    @PatchMapping("/roles")
    public void assignRole(
            @RequestHeader("X-Admin-Code") String adminCode,
            @RequestBody RoleAssignment assignment
    ) {
        codeVerifier.verify(adminCode);
        service.assignRole(assignment.username(), assignment.role());
    }

    @PostMapping("/results")
    public void submitResult(
            @RequestHeader("X-Admin-Code") String adminCode,
            @RequestBody ResultSubmission submission
    ) {
        codeVerifier.verify(adminCode);
        service.submitResult(submission);
    }

    public record RoleAssignment(String username, Role role) {
    }
}

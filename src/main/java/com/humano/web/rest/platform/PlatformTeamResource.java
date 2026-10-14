package com.humano.web.rest.platform;

import com.humano.dto.platform.PlatformTeamMember;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.platform.PlatformTeamService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The platform's own team: anyone on it can see it; owners invite, re-role, deactivate and remove. */
@RestController
@RequestMapping("/api/platform/team")
@RequirePermission(PermissionsConstants.MANAGE_PLATFORM_USERS)
public class PlatformTeamResource {

    public record InviteRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @Size(max = 50) String firstName,
        @Size(max = 50) String lastName,
        @NotBlank String role
    ) {}

    public record UpdateRequest(String role, Boolean activated) {}

    private final PlatformTeamService teamService;

    public PlatformTeamResource(PlatformTeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping
    @RequirePermission(PermissionsConstants.VIEW_PLATFORM_TENANTS)
    public ResponseEntity<List<PlatformTeamMember>> list() {
        return ResponseEntity.ok(teamService.list());
    }

    @PostMapping
    public ResponseEntity<PlatformTeamMember> invite(@Valid @RequestBody InviteRequest request) {
        return ResponseEntity.ok(teamService.invite(request.email(), request.firstName(), request.lastName(), request.role()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<PlatformTeamMember> update(@PathVariable UUID id, @RequestBody UpdateRequest request) {
        return ResponseEntity.ok(teamService.update(id, request.role(), request.activated()));
    }

    @PostMapping("/{id}/resend-invite")
    public ResponseEntity<Void> resendInvite(@PathVariable UUID id) {
        teamService.resendInvite(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable UUID id) {
        teamService.remove(id);
        return ResponseEntity.noContent().build();
    }
}

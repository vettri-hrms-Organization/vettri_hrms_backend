package com.haodaone.auth.controller;

import com.haodaone.auth.dto.ActivateInvitationRequest;
import com.haodaone.employee.dto.InvitationValidationResponse;
import com.haodaone.employee.service.EmployeeInvitationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/invitation")
public class InvitationController {
    private final EmployeeInvitationService invitationService;

    public InvitationController(EmployeeInvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @GetMapping("/validate")
    public ResponseEntity<InvitationValidationResponse> validate(@RequestParam String token) {
        return ResponseEntity.ok(invitationService.validate(token));
    }

    @PostMapping("/activate")
    public ResponseEntity<Void> activate(@Valid @RequestBody ActivateInvitationRequest request) {
        invitationService.activate(request.getToken(), request.getPassword());
        return ResponseEntity.noContent().build();
    }
}
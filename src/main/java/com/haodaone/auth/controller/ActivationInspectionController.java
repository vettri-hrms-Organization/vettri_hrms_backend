package com.haodaone.auth.controller;

import com.haodaone.employee.dto.InvitationValidationResponse;
import com.haodaone.employee.service.EmployeeInvitationService;
import com.haodaone.auth.dto.ActivateInvitationRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/auth/activate")
public class ActivationInspectionController {
    private static final Logger log = LoggerFactory.getLogger(ActivationInspectionController.class);
    private final EmployeeInvitationService invitationService;

    public ActivationInspectionController(EmployeeInvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @GetMapping("/inspect")
    public ResponseEntity<InvitationValidationResponse> inspect(@RequestParam String token) {
        return ResponseEntity.ok(invitationService.validate(token));
    }

    @PostMapping
    public ResponseEntity<Void> activate(@Valid @RequestBody ActivateInvitationRequest request) {
        log.info("Activation request received tokenPresent={} passwordPresent={}",
            request.getToken() != null && !request.getToken().isBlank(),
            request.getPassword() != null && !request.getPassword().isBlank());
        invitationService.activate(request.getToken(), request.getPassword());
        return ResponseEntity.noContent().build();
    }
}
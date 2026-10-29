package com.haodaone.monitoring.controller;

import com.haodaone.monitoring.dto.AgentReleaseDTO;
import com.haodaone.monitoring.service.AgentReleaseService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Platform-level: the Vettri team publishes agent builds here; every customer's agents upgrade from it. */
@RestController
@RequestMapping("/api/admin/agent-releases")
@PreAuthorize("@companySecurity.isSuperAdmin()")
public class AgentReleaseController {

    private final AgentReleaseService service;

    public AgentReleaseController(AgentReleaseService service) {
        this.service = service;
    }

    @GetMapping
    public List<AgentReleaseDTO.Response> list() {
        return service.list();
    }

    @PostMapping
    public ResponseEntity<AgentReleaseDTO.Response> publish(@Valid @RequestBody AgentReleaseDTO.PublishRequest request) {
        return ResponseEntity.status(201).body(service.publish(request));
    }

    @PostMapping("/{id}/activate")
    public AgentReleaseDTO.Response activate(@PathVariable Long id) {
        return service.activate(id);
    }

    @PostMapping("/pause")
    public ResponseEntity<Void> pause() {
        service.deactivateAll();
        return ResponseEntity.noContent().build();
    }
}

package com.haodaone.org.controller;

import com.haodaone.org.dto.TeamDTO;
import com.haodaone.org.service.TeamService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ORG_VIEW')")
    public List<TeamDTO> listAll() {
        return teamService.listAll();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ORG_MANAGE')")
    public ResponseEntity<TeamDTO> create(@Valid @RequestBody TeamDTO.CreateRequest request) {
        return ResponseEntity.status(201).body(teamService.create(request));
    }
}

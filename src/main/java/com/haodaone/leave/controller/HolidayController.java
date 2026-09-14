package com.haodaone.leave.controller;

import com.haodaone.leave.dto.HolidayDTO;
import com.haodaone.leave.service.HolidayService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/holidays")
public class HolidayController {

    private final HolidayService holidayService;

    public HolidayController(HolidayService holidayService) {
        this.holidayService = holidayService;
    }

    @GetMapping
    @PreAuthorize("hasRole('EMPLOYEE') or hasAuthority('LEAVE_APPLY') or hasAuthority('LEAVE_VIEW') or hasAuthority('LEAVE_MANAGE')")
    public List<HolidayDTO> listAll() {
        return holidayService.listAll();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('LEAVE_MANAGE')")
    public ResponseEntity<HolidayDTO> create(@Valid @RequestBody HolidayDTO.CreateRequest request) {
        return ResponseEntity.status(201).body(holidayService.create(request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('LEAVE_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        holidayService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

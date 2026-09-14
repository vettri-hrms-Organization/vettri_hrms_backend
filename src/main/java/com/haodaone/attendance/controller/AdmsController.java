package com.haodaone.attendance.controller;

import com.haodaone.attendance.service.AttendanceIngestService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Device-facing endpoints - fixed paths/params dictated by the eSSL/ZKTeco
 * ADMS protocol itself, not ours to rename. Left open (see SecurityConfig -
 * /iclock/** is permitAll) because biometric device firmware has no way to
 * attach a JWT; in production this would instead be network-restricted
 * (VPN/firewall allow-list) rather than gated by application auth.
 */
@RestController
@RequestMapping("/iclock")
public class AdmsController {

    private static final Logger log = LoggerFactory.getLogger(AdmsController.class);

    private final AttendanceIngestService ingestService;

    public AdmsController(AttendanceIngestService ingestService) {
        this.ingestService = ingestService;
    }

    @GetMapping(value = "/cdata", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> handshake(@RequestParam("SN") String serialNumber,
                                             @RequestParam(value = "pushver", required = false) String pushVersion,
                                             HttpServletRequest request) {
        try {
            return ResponseEntity.ok(ingestService.handleHandshake(serialNumber, pushVersion, request.getRemoteAddr()));
        } catch (Exception ex) {
            log.error("ADMS handshake failed for SN={}: {}", serialNumber, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("ERROR");
        }
    }

    @PostMapping(value = "/cdata", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> pushData(@RequestParam("SN") String serialNumber,
                                            @RequestParam(value = "table", required = false) String table,
                                            @RequestBody(required = false) String body,
                                            HttpServletRequest request) {
        try {
            if (table != null && !table.equalsIgnoreCase("ATTLOG")) {
                return ResponseEntity.ok("OK");
            }
            ingestService.handleAttendanceLogs(serialNumber, body, request.getRemoteAddr());
            return ResponseEntity.ok("OK");
        } catch (Exception ex) {
            log.error("ADMS ATTLOG push failed for SN={}: {}", serialNumber, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("ERROR");
        }
    }

    @GetMapping(value = "/getrequest", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getRequest(@RequestParam("SN") String serialNumber) {
        return ResponseEntity.ok(ingestService.handleGetRequest(serialNumber));
    }
}

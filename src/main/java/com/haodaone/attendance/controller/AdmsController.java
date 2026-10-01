package com.haodaone.attendance.controller;

import com.haodaone.attendance.service.AttendanceIngestService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Device-facing endpoints - fixed paths/params dictated by the eSSL/ZKTeco
 * ADMS protocol itself, not ours to rename. Left open (see SecurityConfig -
 * /iclock/** is permitAll) because biometric device firmware has no way to
 * attach a JWT; requests therefore require an exact source IP allow-list and
 * a device pre-registered to a company. The allow-list is empty by default.
 */
@RestController
@RequestMapping("/iclock")
public class AdmsController {

    private static final Logger log = LoggerFactory.getLogger(AdmsController.class);

    private final AttendanceIngestService ingestService;
    private final Set<String> allowedIps;

    public AdmsController(AttendanceIngestService ingestService,
                          @Value("${attendance.adms.allowed-ips:}") String allowedIps) {
        this.ingestService = ingestService;
        this.allowedIps = Arrays.stream(allowedIps.split(","))
                .map(String::trim)
                .filter(ip -> !ip.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @GetMapping(value = "/cdata", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> handshake(@RequestParam("SN") String serialNumber,
                                             @RequestParam(value = "pushver", required = false) String pushVersion,
                                             HttpServletRequest request) {
        if (!isAllowedSource(request)) return forbidden();
        try {
            return ResponseEntity.ok(ingestService.handleHandshake(serialNumber, pushVersion, request.getRemoteAddr()));
        } catch (AccessDeniedException ex) {
            log.warn("Rejected ADMS handshake for unregistered device SN={}", serialNumber);
            return forbidden();
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
        if (!isAllowedSource(request)) return forbidden();
        try {
            ingestService.validateRegisteredDevice(serialNumber, request.getRemoteAddr());
            if (table != null && !table.equalsIgnoreCase("ATTLOG")) {
                return ResponseEntity.ok("OK");
            }
            ingestService.handleAttendanceLogs(serialNumber, body, request.getRemoteAddr());
            return ResponseEntity.ok("OK");
        } catch (AccessDeniedException ex) {
            log.warn("Rejected ADMS data push for unregistered device SN={}", serialNumber);
            return forbidden();
        } catch (Exception ex) {
            log.error("ADMS ATTLOG push failed for SN={}: {}", serialNumber, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("ERROR");
        }
    }

    @GetMapping(value = "/getrequest", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getRequest(@RequestParam("SN") String serialNumber, HttpServletRequest request) {
        if (!isAllowedSource(request)) return forbidden();
        try {
            return ResponseEntity.ok(ingestService.handleGetRequest(serialNumber, request.getRemoteAddr()));
        } catch (AccessDeniedException ex) {
            log.warn("Rejected ADMS poll for unregistered device SN={}", serialNumber);
            return forbidden();
        } catch (Exception ex) {
            log.error("ADMS poll failed for SN={}: {}", serialNumber, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("ERROR");
        }
    }

    private boolean isAllowedSource(HttpServletRequest request) {
        return !allowedIps.isEmpty() && allowedIps.contains(request.getRemoteAddr());
    }

    private ResponseEntity<String> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(MediaType.TEXT_PLAIN).body("ERROR");
    }
}

package com.haodaone.leave.controller;

import com.haodaone.leave.dto.LeavePolicyDTOs.AdjustmentRequest;
import com.haodaone.leave.dto.LeavePolicyDTOs.AdjustmentResult;
import com.haodaone.leave.dto.LeavePolicyDTOs.FinalSettlementPreview;
import com.haodaone.leave.dto.LeavePolicyDTOs.LedgerEntry;
import com.haodaone.leave.dto.LeavePolicyDTOs.YearEndPreview;
import com.haodaone.leave.dto.LeaveTypeDTO;
import com.haodaone.leave.service.CompOffService;
import com.haodaone.leave.service.LeaveBalanceService;
import com.haodaone.leave.service.LeaveEncashmentService;
import com.haodaone.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** HR leave-policy operations: balance ledger, manual adjustments, year-end and final-settlement encashment preview (V45). */
@RestController
@RequestMapping("/api/leave-policy")
public class LeavePolicyController {

    private final LeaveBalanceService leaveBalanceService;
    private final LeaveEncashmentService leaveEncashmentService;
    private final CompOffService compOffService;

    public LeavePolicyController(LeaveBalanceService leaveBalanceService, LeaveEncashmentService leaveEncashmentService,
                                 CompOffService compOffService) {
        this.leaveBalanceService = leaveBalanceService;
        this.leaveEncashmentService = leaveEncashmentService;
        this.compOffService = compOffService;
    }

    /** Same visibility as the balance endpoint: self, or leave viewers/appliers in scope. */
    @GetMapping("/ledger/{employeeId}")
    @PreAuthorize("@authorizationService.isAllowed('LEAVE_VIEW', 'EMPLOYEE', #employeeId) or hasAuthority('LEAVE_APPLY') and @authorizationService.isAllowed('LEAVE_APPLY', 'EMPLOYEE', #employeeId) or @employeeSecurity.isSelf(#employeeId)")
    public List<LedgerEntry> ledger(@PathVariable Long employeeId, @RequestParam(required = false) Integer year) {
        return leaveBalanceService.ledger(employeeId, year != null ? year : LeaveBalanceService.today().getYear());
    }

    @PostMapping("/adjustments")
    @PreAuthorize("@authorizationService.hasOrganizationScope('LEAVE_MANAGE')")
    public ResponseEntity<AdjustmentResult> adjust(@RequestBody AdjustmentRequest request) {
        return ResponseEntity.status(201).body(leaveBalanceService.adjust(request));
    }

    @GetMapping("/year-end")
    @PreAuthorize("@authorizationService.hasOrganizationScope('LEAVE_MANAGE')")
    public YearEndPreview previewYearEnd(@RequestParam(required = false) Integer year) {
        return leaveBalanceService.previewYearEnd(requiredTenant(),year != null ? year : LeaveBalanceService.today().getYear() - 1);
    }

    @PostMapping("/year-end/run")
    @PreAuthorize("@authorizationService.hasOrganizationScope('LEAVE_MANAGE')")
    public YearEndPreview runYearEnd(@RequestParam Integer year) {
        return leaveBalanceService.runYearEnd(requiredTenant(), year);
    }

    /** Materialise accrued-to-date allocations now (the monthly job does the same). */
    @PostMapping("/accruals/run")
    @PreAuthorize("@authorizationService.hasOrganizationScope('LEAVE_MANAGE')")
    public Map<String, Object> runAccruals() {
        int rows = leaveBalanceService.materialiseAccruals(requiredTenant(), LeaveBalanceService.today());
        return Map.of("rowsWritten", rows, "asOf", LeaveBalanceService.today().toString());
    }

    @GetMapping("/final-settlement/{employeeId}")
    @PreAuthorize("@authorizationService.hasOrganizationScope('LEAVE_MANAGE')")
    public FinalSettlementPreview finalSettlement(@PathVariable Long employeeId) {
        return leaveEncashmentService.finalSettlementPreview(employeeId);
    }

    /** Creates the company's Compensatory Off (CO) type if missing so its expiry can be configured up front. */
    @PostMapping("/comp-off-type")
    @PreAuthorize("@authorizationService.hasOrganizationScope('LEAVE_MANAGE')")
    public LeaveTypeDTO ensureCompOffType() {
        return LeaveTypeDTO.from(compOffService.ensureCompOffType(requiredTenant()));
    }

    private static Long requiredTenant() {
        Long tenant = TenantContext.getCurrentTenant();
        if (tenant == null) throw new com.haodaone.common.exception.BadRequestException("Company context is required");
        return tenant;
    }
}

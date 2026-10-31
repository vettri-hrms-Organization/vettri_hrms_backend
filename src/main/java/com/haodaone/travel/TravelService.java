package com.haodaone.travel;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.notifications.service.NotificationService;
import com.haodaone.performance.suite.PerfContext;
import com.haodaone.salary.adjustment.PayrollDeductionProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

/**
 * Travel requests and cash advances. An employee plans a trip (where, when, how, estimated cost) and may ask for an
 * advance. The reporting manager - or finance (EXPENSE_MANAGE org-wide) - approves; finance pays the advance and, after
 * the trip, settles it against what was actually spent. Unspent advance is either returned or recovered through payroll
 * (this service is a {@link PayrollDeductionProvider}); an advance still open when someone leaves is recovered in F&F.
 */
@Service
public class TravelService implements PayrollDeductionProvider {

    static final String SOURCE = "TRAVEL_ADVANCE";
    public static final Set<String> MODES = Set.of("FLIGHT", "TRAIN", "BUS", "CAB", "OWN_VEHICLE", "OTHER");
    public static final String ADV_NONE = "NONE", ADV_REQUESTED = "REQUESTED", ADV_PAID = "PAID", ADV_SETTLED = "SETTLED",
            ADV_RECOVERY = "RECOVERY", ADV_RECOVERED = "RECOVERED";

    private final TravelRequestRepository travel;
    private final PerfContext ctx;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public TravelService(TravelRequestRepository travel, PerfContext ctx, NotificationService notificationService,
                         AuditLogService auditLogService, Clock applicationClock) {
        this.travel = travel;
        this.ctx = ctx;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.clock = applicationClock;
    }

    public record TripRequest(String purpose, String tripType, String fromCity, String toCity, LocalDate departDate, LocalDate returnDate,
                              String travelMode, Boolean needsAccommodation, BigDecimal estimatedCost, BigDecimal advanceAmount, String notes) { }
    public record DecisionRequest(Boolean approve, String note) { }
    public record SettleRequest(BigDecimal spentAmount, String excessHandling) { }
    public record TripDTO(Long id, Long employeeId, String employeeName, String employeeCode, String purpose, String tripType, String fromCity, String toCity,
                          LocalDate departDate, LocalDate returnDate, long nights, String travelMode, boolean needsAccommodation, BigDecimal estimatedCost,
                          BigDecimal advanceAmount, String notes, String status, String approverName, String decidedByName, LocalDateTime decidedAt,
                          String decisionNote, String advanceStatus, LocalDateTime advancePaidAt, String advanceReference, BigDecimal spentAmount,
                          BigDecimal recoveryAmount, LocalDateTime createdAt, boolean canDecide, boolean canPay) { }

    // ------------------------------------------------------------------ employee

    @Transactional
    public TripDTO create(TripRequest r) {
        Employee me = ctx.me();
        if (r == null) throw new BadRequestException("Describe the trip");
        String purpose = PerfContext.text(r.purpose(), 300);
        String from = PerfContext.text(r.fromCity(), 80);
        String to = PerfContext.text(r.toCity(), 80);
        if (purpose == null || from == null || to == null) throw new BadRequestException("Add the purpose, from and to");
        if (r.departDate() == null || r.returnDate() == null || r.returnDate().isBefore(r.departDate())) throw new BadRequestException("Choose valid travel dates");
        if (r.departDate().isBefore(LocalDate.now(clock).minusDays(30))) throw new BadRequestException("Trips can be recorded up to 30 days after they start");
        String mode = r.travelMode() == null ? "OTHER" : r.travelMode().toUpperCase(Locale.ROOT);
        if (!MODES.contains(mode)) throw new BadRequestException("Unknown travel mode");
        String type = "INTERNATIONAL".equalsIgnoreCase(r.tripType()) ? "INTERNATIONAL" : "DOMESTIC";
        BigDecimal advance = r.advanceAmount() == null ? BigDecimal.ZERO : r.advanceAmount();
        if (advance.signum() < 0 || (r.estimatedCost() != null && r.estimatedCost().signum() < 0)) throw new BadRequestException("Amounts can't be negative");
        if (r.estimatedCost() != null && advance.compareTo(r.estimatedCost()) > 0) throw new BadRequestException("The advance can't be more than the estimated cost");
        TravelRequest t = new TravelRequest();
        t.setCompanyId(ctx.companyId());
        t.setEmployeeId(me.getId());
        t.setPurpose(purpose);
        t.setTripType(type);
        t.setFromCity(from);
        t.setToCity(to);
        t.setDepartDate(r.departDate());
        t.setReturnDate(r.returnDate());
        t.setTravelMode(mode);
        t.setNeedsAccommodation(Boolean.TRUE.equals(r.needsAccommodation()));
        t.setEstimatedCost(r.estimatedCost());
        t.setAdvanceAmount(advance);
        t.setNotes(PerfContext.text(r.notes(), 1000));
        t.setStatus(TravelRequest.PENDING);
        t.setAdvanceStatus(advance.signum() > 0 ? ADV_REQUESTED : ADV_NONE);
        t.setApproverEmployeeId(me.getReportingManager() == null ? null : me.getReportingManager().getId());
        t = travel.save(t);
        String msg = me.getFullName() + " requests travel " + from + " → " + to + " (" + r.departDate() + ")" + (advance.signum() > 0 ? " with an advance of Rs " + advance.toPlainString() : "") + ".";
        if (t.getApproverEmployeeId() != null) {
            ctx.notify(me.getReportingManager(), "TRAVEL", "Travel request to approve", msg, "TRAVEL_REQUEST", t.getId());
        } else {
            notificationService.notifyPermissionRecipients(ctx.companyId(), me.getId(), "EXPENSE_MANAGE", "TRAVEL", "Travel request to approve", msg, "TRAVEL_REQUEST", t.getId());
        }
        return toDto(t, me);
    }

    @Transactional(readOnly = true)
    public List<TripDTO> mine() {
        Employee me = ctx.me();
        return travel.findAllByCompanyIdAndEmployeeIdAndDeletedFalseOrderByDepartDateDesc(ctx.companyId(), me.getId()).stream().map(t -> toDto(t, me)).toList();
    }

    @Transactional
    public TripDTO cancel(Long id) {
        Employee me = ctx.me();
        TravelRequest t = trip(id);
        if (!me.getId().equals(t.getEmployeeId())) throw new AccessDeniedException("Not your trip");
        if (!TravelRequest.PENDING.equals(t.getStatus()) && !TravelRequest.APPROVED.equals(t.getStatus())) throw new BadRequestException("This trip can't be cancelled now");
        if (ADV_PAID.equals(t.getAdvanceStatus())) throw new BadRequestException("The advance is already paid - ask finance to settle it");
        t.setStatus(TravelRequest.CANCELLED);
        if (ADV_REQUESTED.equals(t.getAdvanceStatus())) t.setAdvanceStatus(ADV_NONE);
        travel.save(t);
        return toDto(t, me);
    }

    // ------------------------------------------------------------------ approvers and finance

    /** Pending approvals for a manager; everything for finance. */
    @Transactional(readOnly = true)
    public List<TripDTO> queue(String status) {
        Employee me = ctx.meOrNull();
        boolean finance = isFinance();
        return travel.findAllByCompanyIdAndDeletedFalseOrderByDepartDateDesc(ctx.companyId()).stream()
                .filter(t -> finance || (me != null && me.getId().equals(t.getApproverEmployeeId())))
                .filter(t -> status == null || status.isBlank() || "ALL".equalsIgnoreCase(status) || status.equalsIgnoreCase(t.getStatus())
                        || ("ADVANCES".equalsIgnoreCase(status) && Set.of(ADV_REQUESTED, ADV_PAID, ADV_RECOVERY).contains(t.getAdvanceStatus())
                        && !TravelRequest.REJECTED.equals(t.getStatus()) && !TravelRequest.CANCELLED.equals(t.getStatus())))
                .map(t -> toDto(t, me)).toList();
    }

    @Transactional
    public TripDTO decide(Long id, DecisionRequest r, String actor) {
        Employee me = ctx.meOrNull();
        TravelRequest t = trip(id);
        if (!canDecide(t, me)) throw new AccessDeniedException("Only the reporting manager or finance can decide this trip");
        if (!TravelRequest.PENDING.equals(t.getStatus())) throw new BadRequestException("This trip is not waiting for approval");
        boolean approve = r != null && Boolean.TRUE.equals(r.approve());
        String note = PerfContext.text(r == null ? null : r.note(), 500);
        if (!approve && note == null) throw new BadRequestException("Tell the employee why");
        t.setStatus(approve ? TravelRequest.APPROVED : TravelRequest.REJECTED);
        if (!approve && ADV_REQUESTED.equals(t.getAdvanceStatus())) t.setAdvanceStatus(ADV_NONE);
        t.setDecidedByName(actor);
        t.setDecidedAt(LocalDateTime.now(clock));
        t.setDecisionNote(note);
        travel.save(t);
        Employee owner = ctx.people(List.of(t.getEmployeeId())).get(t.getEmployeeId());
        ctx.notify(owner, "TRAVEL", approve ? "Trip approved" : "Trip not approved",
                t.getFromCity() + " → " + t.getToCity() + " on " + t.getDepartDate() + (approve ? " is approved." : ": " + note), "TRAVEL_REQUEST", t.getId());
        if (approve && ADV_REQUESTED.equals(t.getAdvanceStatus()))
            notificationService.notifyPermissionRecipients(ctx.companyId(), t.getEmployeeId(), "EXPENSE_MANAGE", "TRAVEL", "Travel advance to pay",
                    (owner == null ? "An employee" : owner.getFullName()) + " has an approved advance of Rs " + t.getAdvanceAmount().toPlainString() + ".", "TRAVEL_REQUEST", t.getId());
        auditLogService.log("TRAVEL_REQUEST", t.getId(), approve ? "APPROVED" : "REJECTED", actor + (approve ? " approved" : " rejected") + " the trip");
        return toDto(t, me);
    }

    @Transactional
    public TripDTO payAdvance(Long id, String reference, String actor) {
        requireFinance();
        TravelRequest t = trip(id);
        if (!TravelRequest.APPROVED.equals(t.getStatus()) || !ADV_REQUESTED.equals(t.getAdvanceStatus())) throw new BadRequestException("No approved advance to pay");
        String ref = PerfContext.text(reference, 100);
        if (ref == null) throw new BadRequestException("Enter the payment reference (UTR / cheque no.)");
        t.setAdvanceStatus(ADV_PAID);
        t.setAdvancePaidAt(LocalDateTime.now(clock));
        t.setAdvanceReference(ref);
        travel.save(t);
        ctx.notify(ctx.people(List.of(t.getEmployeeId())).get(t.getEmployeeId()), "TRAVEL", "Travel advance paid",
                "Rs " + t.getAdvanceAmount().toPlainString() + " has been paid (ref " + ref + "). Keep your bills to settle it after the trip.", "TRAVEL_REQUEST", t.getId());
        auditLogService.log("TRAVEL_REQUEST", t.getId(), "ADVANCE_PAID", actor + " paid advance " + t.getAdvanceAmount() + " ref " + ref);
        return toDto(t, ctx.meOrNull());
    }

    /**
     * After the trip: record what was spent. If the advance was larger, the difference is either RETURNED by the employee
     * or recovered in the next PAYROLL. If it was smaller, the employee claims the rest through expenses.
     */
    @Transactional
    public TripDTO settle(Long id, SettleRequest r, String actor) {
        requireFinance();
        TravelRequest t = trip(id);
        if (!TravelRequest.APPROVED.equals(t.getStatus())) throw new BadRequestException("Only approved trips can be settled");
        if (r == null || r.spentAmount() == null || r.spentAmount().signum() < 0) throw new BadRequestException("Enter the amount spent");
        t.setSpentAmount(r.spentAmount());
        t.setStatus(TravelRequest.COMPLETED);
        if (ADV_PAID.equals(t.getAdvanceStatus())) {
            BigDecimal excess = t.getAdvanceAmount().subtract(r.spentAmount());
            if (excess.signum() > 0 && "PAYROLL".equalsIgnoreCase(r.excessHandling())) {
                t.setRecoveryAmount(excess);
                t.setAdvanceStatus(ADV_RECOVERY);
            } else {
                t.setAdvanceStatus(ADV_SETTLED);
            }
        }
        travel.save(t);
        auditLogService.log("TRAVEL_REQUEST", t.getId(), "SETTLED", actor + " settled the trip: spent " + r.spentAmount() + ", advance " + t.getAdvanceAmount() + " -> " + t.getAdvanceStatus());
        return toDto(t, ctx.meOrNull());
    }

    // ------------------------------------------------------------------ payroll

    @Override
    public Set<String> sourceTypes() {
        return Set.of(SOURCE);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayrollDeduction> deductionsFor(Long companyId, Long employeeId, YearMonth period) {
        return travel.findAllByCompanyIdAndEmployeeIdAndAdvanceStatusAndDeletedFalse(companyId, employeeId, ADV_RECOVERY).stream()
                .filter(t -> t.getRecoveryAmount() != null && t.getRecoveryAmount().signum() > 0)
                .map(t -> new PayrollDeduction("TRAVEL_ADV", "Travel advance recovery (" + t.getToCity() + ")", t.getRecoveryAmount(), SOURCE, t.getId())).toList();
    }

    @Override
    @Transactional
    public void markSettled(List<PayrollDeduction> deductions, Long payrollItemId) {
        for (PayrollDeduction d : deductions) {
            if (!SOURCE.equals(d.sourceType())) continue;
            travel.findById(d.sourceId()).ifPresent(t -> {
                t.setAdvanceStatus(ADV_RECOVERED);
                t.setRecoveryPayrollItemId(payrollItemId);
                travel.save(t);
            });
        }
    }

    /** Full & final: an unsettled paid advance is recovered whole; a pending payroll recovery is recovered as planned. */
    @Override
    @Transactional(readOnly = true)
    public List<PayrollDeduction> settlementRecoveries(Long companyId, Long employeeId) {
        List<PayrollDeduction> out = new ArrayList<>(deductionsFor(companyId, employeeId, null));
        travel.findAllByCompanyIdAndEmployeeIdAndAdvanceStatusAndDeletedFalse(companyId, employeeId, ADV_PAID).forEach(t ->
                out.add(new PayrollDeduction("TRAVEL_ADV", "Unsettled travel advance (" + t.getToCity() + ")", t.getAdvanceAmount(), SOURCE, t.getId())));
        return out;
    }

    @Override
    @Transactional
    public void markRecovered(List<PayrollDeduction> recoveries, Long settlementMarker) {
        markSettled(recoveries, settlementMarker);
    }

    // ------------------------------------------------------------------ helpers

    private boolean isFinance() {
        return ctx.hasOrgScope("EXPENSE_MANAGE");
    }

    private void requireFinance() {
        if (!isFinance()) throw new AccessDeniedException("Only finance can do this");
    }

    private boolean canDecide(TravelRequest t, Employee me) {
        if (me != null && me.getId().equals(t.getEmployeeId())) return false;
        return isFinance() || (me != null && me.getId().equals(t.getApproverEmployeeId()));
    }

    private TravelRequest trip(Long id) {
        TravelRequest t = travel.findByIdAndCompanyIdAndDeletedFalse(id, ctx.companyId()).orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        Employee me = ctx.meOrNull();
        boolean owner = me != null && me.getId().equals(t.getEmployeeId());
        boolean approver = me != null && me.getId().equals(t.getApproverEmployeeId());
        if (!owner && !approver && !isFinance()) throw new ResourceNotFoundException("Trip not found");
        return t;
    }

    private TripDTO toDto(TravelRequest t, Employee me) {
        Map<Long, Employee> people = ctx.people(java.util.stream.Stream.of(t.getEmployeeId(), t.getApproverEmployeeId()).filter(Objects::nonNull).toList());
        Employee e = people.get(t.getEmployeeId());
        long nights = java.time.temporal.ChronoUnit.DAYS.between(t.getDepartDate(), t.getReturnDate());
        boolean finance = isFinance();
        return new TripDTO(t.getId(), t.getEmployeeId(), e == null ? null : e.getFullName(), e == null ? null : e.getEmployeeCode(), t.getPurpose(),
                t.getTripType(), t.getFromCity(), t.getToCity(), t.getDepartDate(), t.getReturnDate(), nights, t.getTravelMode(), t.isNeedsAccommodation(),
                t.getEstimatedCost(), t.getAdvanceAmount(), t.getNotes(), t.getStatus(),
                t.getApproverEmployeeId() == null ? "Finance" : PerfContext.name(people, t.getApproverEmployeeId()), t.getDecidedByName(), t.getDecidedAt(),
                t.getDecisionNote(), t.getAdvanceStatus(), t.getAdvancePaidAt(), t.getAdvanceReference(), t.getSpentAmount(), t.getRecoveryAmount(),
                t.getCreatedAt(), TravelRequest.PENDING.equals(t.getStatus()) && canDecide(t, me),
                finance && (me == null || !me.getId().equals(t.getEmployeeId())));
    }
}

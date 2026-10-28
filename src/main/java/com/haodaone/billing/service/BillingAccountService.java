package com.haodaone.billing.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.billing.dto.BillingDtos.*;
import com.haodaone.billing.dto.InvoiceResponse;
import com.haodaone.billing.entity.Invoice;
import com.haodaone.billing.entity.InvoiceStatus;
import com.haodaone.billing.entity.InvoiceType;
import com.haodaone.billing.entity.SeatAddOnCharge;
import com.haodaone.billing.gst.FinancialYear;
import com.haodaone.billing.gst.GstinValidator;
import com.haodaone.billing.gst.IndianStates;
import com.haodaone.billing.repository.InvoiceRepository;
import com.haodaone.billing.repository.SeatAddOnChargeRepository;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.company.entity.Company;
import com.haodaone.company.entity.Plan;
import com.haodaone.company.entity.Subscription;
import com.haodaone.company.entity.SubscriptionStatus;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.company.repository.SubscriptionRepository;
import com.haodaone.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read models for the billing page / banner, the GST billing profile, platform-admin billing and credit notes. */
@Service
public class BillingAccountService {

    private static final List<String> PAID_PLANS = List.of(BillingPricing.STARTER, BillingPricing.BUSINESS, BillingPricing.ENTERPRISE);
    private static final Map<String, String> PLAN_LABELS = Map.of(
            BillingPricing.STARTER, "Starter", BillingPricing.BUSINESS, "Business", BillingPricing.ENTERPRISE, "Enterprise");

    private final SubscriptionRepository subscriptions;
    private final CompanyRepository companies;
    private final InvoiceRepository invoices;
    private final SeatAddOnChargeRepository addOns;
    private final SeatCountService seats;
    private final BillingLifecycleService lifecycle;
    private final InvoiceNumberService numbers;
    private final BillingSettings settings;
    private final AuditLogService audit;

    public BillingAccountService(SubscriptionRepository subscriptions, CompanyRepository companies, InvoiceRepository invoices,
                                 SeatAddOnChargeRepository addOns, SeatCountService seats, BillingLifecycleService lifecycle,
                                 InvoiceNumberService numbers, BillingSettings settings, AuditLogService audit) {
        this.subscriptions = subscriptions;
        this.companies = companies;
        this.invoices = invoices;
        this.addOns = addOns;
        this.seats = seats;
        this.lifecycle = lifecycle;
        this.numbers = numbers;
        this.settings = settings;
        this.audit = audit;
    }

    // ------------------------------------------------------------- banner

    @Transactional(readOnly = true)
    public BillingStatusResponse status(Long companyId, Collection<String> roles) {
        Subscription sub = companyId == null ? null : subscriptions.findByCompany_IdAndDeletedFalse(companyId).orElse(null);
        if (sub == null) {
            return new BillingStatusResponse("NONE", null, null, null, null, null, false, false, false, false, null, null, null);
        }
        boolean companyAdmin = roles.contains("COMPANY_ADMIN");
        boolean billingViewer = companyAdmin || roles.contains("HR_ADMIN") || roles.contains("SUPER_ADMIN");
        LocalDate today = BillingLifecycleService.today();
        Long daysLeft = sub.getRenewalDate() == null ? null : ChronoUnit.DAYS.between(today, sub.getRenewalDate());
        Invoice due = billingViewer ? lifecycle.pendingInvoice(sub).orElse(null) : null;
        return new BillingStatusResponse(
                sub.getStatus().name(),
                sub.getPlan() == null ? null : sub.getPlan().name(),
                sub.getBillingCycle(),
                sub.getRenewalDate(),
                daysLeft,
                sub.getGraceEndsOn(),
                BillingLifecycleService.isReadOnly(sub.getStatus()),
                BillingLifecycleService.isTrial(sub.getStatus()),
                companyAdmin && sub.getStatus() != SubscriptionStatus.PENDING_PAYMENT,
                sub.isCancelAtPeriodEnd(),
                sub.getSuspensionReason(),
                due == null ? null : due.getId(),
                due == null ? null : due.getTotal());
    }

    // ----------------------------------------------------------- overview

    @Transactional(readOnly = true)
    public SubscriptionOverview overview(Long companyId) {
        Subscription sub = subscription(companyId);
        Company company = sub.getCompany();
        Invoice due = lifecycle.pendingInvoice(sub).orElse(null);
        BigDecimal unbilled = addOns.findBySubscription_IdAndInvoiceIsNullAndDeletedFalseOrderByEffectiveDateAsc(sub.getId()).stream()
                .map(SeatAddOnCharge::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SubscriptionOverview(
                sub.getId(), sub.getStatus().name(), sub.getPlan() == null ? null : sub.getPlan().name(), sub.getBillingCycle(),
                sub.getRate(), sub.getBillableEmployeeCount(), seats.billableSeats(company.getId()), sub.getEmployeeLimit(),
                sub.getStartDate(), sub.getRenewalDate(), sub.getGraceEndsOn(), sub.getPendingPlan(), sub.getPendingBillingCycle(),
                sub.isCancelAtPeriodEnd(), sub.getRazorpaySubscriptionId() != null, settings.razorpaySubscriptionsEnabled(),
                unbilled, due == null ? null : InvoiceResponse.from(due), plans(), profileOf(company));
    }

    public static List<PlanOption> plans() {
        return PAID_PLANS.stream().map(plan -> new PlanOption(plan, PLAN_LABELS.get(plan),
                BillingPricing.rateFor(plan, BillingPricing.MONTHLY),
                BillingPricing.rateFor(plan, BillingPricing.QUARTERLY),
                BillingPricing.rateFor(plan, BillingPricing.ANNUAL))).toList();
    }

    // ------------------------------------------------------------ profile

    @Transactional(readOnly = true)
    public BillingProfile profile(Long companyId) {
        return profileOf(company(companyId));
    }

    @Transactional
    public BillingProfile updateProfile(Long companyId, BillingProfileRequest request) {
        Company company = company(companyId);
        String gstin = blankToNull(GstinValidator.normalize(request.gstin()));
        if (gstin != null && !GstinValidator.isValid(gstin)) {
            throw new BadRequestException("The GSTIN is not valid. Check the 15 characters (state code, PAN, entity number, Z, check digit).");
        }
        String stateCode = IndianStates.normalizeCode(blankToNull(request.stateCode()));
        if (stateCode != null && !IndianStates.isValidCode(stateCode)) {
            throw new BadRequestException("Unknown GST state code " + stateCode + ".");
        }
        if (gstin != null) {
            String gstinState = GstinValidator.stateCodeOf(gstin);
            if (stateCode == null) stateCode = gstinState;
            else if (!stateCode.equals(gstinState)) {
                throw new BadRequestException("The billing state does not match the state in the GSTIN (" + gstinState + ").");
            }
        }
        String state = blankToNull(request.state());
        if (state == null && stateCode != null) state = IndianStates.nameOf(stateCode);
        company.setLegalName(blankToNull(request.legalName()));
        company.setGstin(gstin);
        company.setBillingAddress(blankToNull(request.billingAddress()));
        company.setBillingStateCode(stateCode);
        company.setBillingState(state);
        company.setBillingPoNumber(blankToNull(request.poNumber()));
        companies.save(company);
        // Re-price the open renewal invoice (place of supply may have changed).
        subscriptions.findByCompany_IdAndDeletedFalse(companyId).ifPresent(sub ->
                lifecycle.pendingInvoice(sub).filter(invoice -> invoice.getRazorpayOrderId() == null).ifPresent(invoice -> {
                    lifecycle.refreshAmounts(invoice, sub);
                    invoices.save(invoice);
                }));
        audit.log("Company", company.getId(), "BILLING_PROFILE_UPDATE",
                "Billing profile updated (GSTIN " + (gstin == null ? "none" : gstin) + ", state " + (stateCode == null ? "-" : stateCode) + ")");
        return profileOf(company);
    }

    private static BillingProfile profileOf(Company company) {
        return new BillingProfile(company.getName(), company.getLegalName(), company.getGstin(), company.getBillingAddress(),
                company.getBillingState(), company.getBillingStateCode(), company.getBillingPoNumber());
    }

    // ---------------------------------------------------- platform admin

    @Transactional(readOnly = true)
    public List<AdminCompanyBilling> adminCompanies() {
        Map<Long, Subscription> byCompany = subscriptions.findAllByDeletedFalse().stream()
                .filter(s -> s.getCompany() != null)
                .collect(Collectors.toMap(s -> s.getCompany().getId(), Function.identity(), (a, b) -> a.getId() > b.getId() ? a : b));
        return companies.findAll().stream().filter(c -> !c.isDeleted())
                .sorted(Comparator.comparing(Company::getName, String.CASE_INSENSITIVE_ORDER))
                .map(company -> {
                    Subscription sub = byCompany.get(company.getId());
                    int billable = seats.billableSeats(company.getId());
                    return new AdminCompanyBilling(company.getId(), company.getName(),
                            sub == null ? null : sub.getId(),
                            sub == null || sub.getPlan() == null ? null : sub.getPlan().name(),
                            sub == null ? "NONE" : sub.getStatus().name(),
                            sub == null ? null : sub.getBillingCycle(),
                            sub == null ? null : sub.getRenewalDate(),
                            sub == null ? null : sub.getGraceEndsOn(),
                            sub == null ? null : sub.getBillableEmployeeCount(),
                            billable,
                            sub == null ? BigDecimal.ZERO : mrr(sub),
                            sub != null && sub.getRazorpaySubscriptionId() != null);
                }).toList();
    }

    /** Monthly recurring revenue (ex-GST): paid seats x per-cycle rate / cycle months, for ACTIVE / PAST_DUE paid plans. */
    public static BigDecimal mrr(Subscription sub) {
        if (sub.getStatus() != SubscriptionStatus.ACTIVE && sub.getStatus() != SubscriptionStatus.PAST_DUE) return BigDecimal.ZERO;
        if (sub.getPlan() == Plan.TRIAL || sub.getRate() == null) return BigDecimal.ZERO;
        int paidSeats = sub.getBillableEmployeeCount() == null ? 0 : sub.getBillableEmployeeCount();
        return sub.getRate().multiply(BigDecimal.valueOf(paidSeats))
                .divide(BigDecimal.valueOf(BillingLifecycleService.cycleMonths(sub.getBillingCycle())), 2, RoundingMode.HALF_UP);
    }

    public Subscription subscriptionById(Long id) {
        return subscriptions.findById(id).filter(s -> !s.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found: " + id));
    }

    /**
     * Credit note for a refund / cancellation: a negative document in the
     * CN series referencing the original paid invoice, with the original GST
     * split scaled to the credited amount.
     */
    @Transactional
    public InvoiceResponse issueCreditNote(Long originalInvoiceId, BigDecimal requestedAmount, String reason) {
        return InvoiceResponse.from(createCreditNote(originalInvoiceId, requestedAmount, reason));
    }

    @Transactional
    public Invoice createCreditNote(Long originalInvoiceId, BigDecimal requestedAmount, String reason) {
        Invoice original = invoices.findById(originalInvoiceId).filter(i -> !i.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + originalInvoiceId));
        if (original.getInvoiceType() == InvoiceType.CREDIT_NOTE || original.getStatus() != InvoiceStatus.PAID) {
            throw new BadRequestException("Credit notes can only be issued against a paid invoice.");
        }
        BigDecimal alreadyCredited = invoices.findByOriginalInvoice_IdAndDeletedFalse(original.getId()).stream()
                .map(Invoice::getTotal).filter(java.util.Objects::nonNull).map(BigDecimal::abs).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = original.getTotal().subtract(alreadyCredited);
        BigDecimal amount = requestedAmount == null ? remaining : requestedAmount.setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0 || amount.compareTo(remaining) > 0) {
            throw new BadRequestException("Credit amount must be between 0 and " + remaining + ".");
        }
        BigDecimal ratio = original.getTotal().signum() == 0 ? BigDecimal.ONE
                : amount.divide(original.getTotal(), 10, RoundingMode.HALF_UP);
        LocalDate today = BillingLifecycleService.today();

        Invoice note = new Invoice();
        note.setInvoiceNumber(numbers.nextCreditNoteNumber(today));
        note.setFinancialYear(FinancialYear.of(today));
        note.setCompany(original.getCompany());
        note.setSubscription(original.getSubscription());
        note.setInvoiceType(InvoiceType.CREDIT_NOTE);
        note.setStatus(InvoiceStatus.ISSUED);
        note.setInvoiceDate(today);
        note.setOriginalInvoice(original);
        note.setBillingPeriodStart(original.getBillingPeriodStart());
        note.setBillingPeriodEnd(original.getBillingPeriodEnd());
        note.setPlan(original.getPlan());
        note.setBillingCycle(original.getBillingCycle());
        note.setCurrency(original.getCurrency());
        BigDecimal taxable = scaleNeg(original.getTaxableValue() != null ? original.getTaxableValue() : original.getSubtotal(), ratio);
        BigDecimal cgst = scaleNeg(original.getCgstAmount(), ratio);
        BigDecimal sgst = scaleNeg(original.getSgstAmount(), ratio);
        BigDecimal igst = scaleNeg(original.getIgstAmount(), ratio);
        BigDecimal tax = cgst.add(sgst).add(igst);
        if (original.getTaxableValue() == null) tax = scaleNeg(original.getTax(), ratio);
        BigDecimal total = amount.negate();
        taxable = total.subtract(tax); // absorb rounding in the taxable value so lines always add up
        note.setSubtotal(taxable);
        note.setBaseAmount(taxable);
        note.setTaxableValue(taxable);
        note.setDiscount(BigDecimal.ZERO);
        note.setCgstRate(original.getCgstRate());
        note.setCgstAmount(cgst);
        note.setSgstRate(original.getSgstRate());
        note.setSgstAmount(sgst);
        note.setIgstRate(original.getIgstRate());
        note.setIgstAmount(igst);
        note.setTax(tax);
        note.setTotal(total);
        note.setTaxNote(original.getTaxNote());
        note.setSacCode(original.getSacCode());
        note.setPlaceOfSupply(original.getPlaceOfSupply());
        note.setPlaceOfSupplyCode(original.getPlaceOfSupplyCode());
        note.setSellerGstin(original.getSellerGstin());
        note.setBuyerLegalName(original.getBuyerLegalName());
        note.setBuyerGstin(original.getBuyerGstin());
        note.setBuyerAddress(original.getBuyerAddress());
        note.setBuyerPoNumber(original.getBuyerPoNumber());
        note.setNotes("Reason: " + reason);
        note.setPaidAt(LocalDateTime.now(BillingLifecycleService.IST));
        note.setEmailStatus("NOT_SENT");
        Invoice saved = invoices.save(note);
        if (amount.compareTo(remaining) == 0) {
            original.setStatus(InvoiceStatus.REFUNDED);
            invoices.save(original);
        }
        Long previous = TenantContext.getCurrentTenant();
        try {
            if (original.getCompany() != null) TenantContext.setCurrentTenant(original.getCompany().getId());
            audit.log("Invoice", saved.getId(), "CREDIT_NOTE",
                    saved.getInvoiceNumber() + " for " + amount + " against " + original.getInvoiceNumber() + ". Reason: " + reason);
        } finally {
            if (previous != null) TenantContext.setCurrentTenant(previous); else TenantContext.clear();
        }
        return saved;
    }

    private static BigDecimal scaleNeg(BigDecimal value, BigDecimal ratio) {
        if (value == null) return new BigDecimal("0.00");
        return value.multiply(ratio).setScale(2, RoundingMode.HALF_UP).negate();
    }

    // ------------------------------------------------------------ helpers

    public Subscription subscription(Long companyId) {
        if (companyId == null) throw new BadRequestException("A company context is required.");
        return subscriptions.findByCompany_IdAndDeletedFalse(companyId)
                .orElseThrow(() -> new BadRequestException("No subscription found for this company."));
    }

    private Company company(Long companyId) {
        if (companyId == null) throw new BadRequestException("A company context is required.");
        return companies.findById(companyId).filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Company not found."));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

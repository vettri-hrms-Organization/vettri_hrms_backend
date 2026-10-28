package com.haodaone.expense.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.expense.dto.ExpenseDtos.CategoryRequest;
import com.haodaone.expense.dto.ExpenseDtos.CategoryView;
import com.haodaone.expense.dto.ExpenseDtos.SettingsRequest;
import com.haodaone.expense.dto.ExpenseDtos.SettingsView;
import com.haodaone.expense.entity.ExpenseCategory;
import com.haodaone.expense.entity.ExpenseSettings;
import com.haodaone.expense.repository.ExpenseCategoryRepository;
import com.haodaone.expense.repository.ExpenseSettingsRepository;
import com.haodaone.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

@Service
public class ExpenseCategoryService {

    /** code, name, mileage rate per km (null = not a mileage category). */
    private static final List<Object[]> DEFAULT_CATEGORIES = List.of(
            new Object[]{"TRAVEL", "Travel", null},
            new Object[]{"FOOD", "Food", null},
            new Object[]{"ACCOMMODATION", "Accommodation", null},
            new Object[]{"FUEL_MILEAGE", "Fuel/Mileage", new BigDecimal("10.00")},
            new Object[]{"INTERNET_PHONE", "Internet/Phone", null},
            new Object[]{"OTHER", "Other", null});

    private final ExpenseCategoryRepository categoryRepository;
    private final ExpenseSettingsRepository settingsRepository;
    private final CompanyRepository companyRepository;
    private final AuditLogService auditLogService;

    public ExpenseCategoryService(ExpenseCategoryRepository categoryRepository,
                                  ExpenseSettingsRepository settingsRepository,
                                  CompanyRepository companyRepository,
                                  AuditLogService auditLogService) {
        this.categoryRepository = categoryRepository;
        this.settingsRepository = settingsRepository;
        this.companyRepository = companyRepository;
        this.auditLogService = auditLogService;
    }

    /** Seeds the standard categories the first time a company touches expenses. */
    @Transactional
    public void ensureDefaults(Long companyId) {
        if (categoryRepository.countByCompany_IdAndDeletedFalse(companyId) > 0) return;
        int order = 0;
        for (Object[] row : DEFAULT_CATEGORIES) {
            ExpenseCategory category = new ExpenseCategory();
            category.setCompany(companyRepository.getReferenceById(companyId));
            category.setCode((String) row[0]);
            category.setName((String) row[1]);
            category.setMileageRatePerKm((BigDecimal) row[2]);
            category.setSortOrder(order++);
            categoryRepository.save(category);
        }
    }

    @Transactional
    public List<CategoryView> list(boolean activeOnly) {
        Long companyId = requiredTenant();
        ensureDefaults(companyId);
        return categoryRepository.findAllByCompany_IdAndDeletedFalseOrderBySortOrderAscNameAsc(companyId).stream()
                .filter(category -> !activeOnly || category.isActive())
                .map(ExpenseCategoryService::toView)
                .toList();
    }

    @Transactional
    public CategoryView create(CategoryRequest request) {
        Long companyId = requiredTenant();
        ensureDefaults(companyId);
        String name = requiredName(request);
        String code = name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (code.isEmpty()) code = "CATEGORY";
        if (code.length() > 30) code = code.substring(0, 30);
        String candidate = code;
        int suffix = 2;
        while (categoryRepository.existsByCompany_IdAndCodeAndDeletedFalse(companyId, candidate)) {
            candidate = code + "_" + suffix++;
        }
        ExpenseCategory category = new ExpenseCategory();
        category.setCompany(companyRepository.getReferenceById(companyId));
        category.setCode(candidate);
        category.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 100);
        apply(category, request, name);
        ExpenseCategory saved = categoryRepository.save(category);
        auditLogService.log("ExpenseCategory", saved.getId(), "CREATE", "Created expense category " + saved.getName());
        return toView(saved);
    }

    @Transactional
    public CategoryView update(Long id, CategoryRequest request) {
        Long companyId = requiredTenant();
        ExpenseCategory category = categoryRepository.findByIdAndCompany_IdAndDeletedFalse(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense category not found."));
        String name = requiredName(request);
        if (request.sortOrder() != null) category.setSortOrder(request.sortOrder());
        apply(category, request, name);
        ExpenseCategory saved = categoryRepository.save(category);
        auditLogService.log("ExpenseCategory", saved.getId(), "UPDATE", "Updated expense category " + saved.getName()
                + " (limit " + saved.getPerClaimLimit() + " " + saved.getLimitAction()
                + ", receipt above " + saved.getReceiptRequiredAbove() + " " + saved.getReceiptAction() + ")");
        return toView(saved);
    }

    @Transactional(readOnly = true)
    public SettingsView settings() {
        return new SettingsView(reimbursementMode(requiredTenant()));
    }

    /** PAYROLL or SEPARATE for the company; SEPARATE when never configured. */
    @Transactional(readOnly = true)
    public String reimbursementMode(Long companyId) {
        return settingsRepository.findFirstByCompany_IdAndDeletedFalse(companyId)
                .map(ExpenseSettings::getReimbursementMode)
                .orElse(ExpenseSettings.MODE_SEPARATE);
    }

    @Transactional
    public SettingsView updateSettings(SettingsRequest request) {
        Long companyId = requiredTenant();
        String mode = request == null || request.reimbursementMode() == null ? ""
                : request.reimbursementMode().trim().toUpperCase(Locale.ROOT);
        if (!ExpenseSettings.MODE_PAYROLL.equals(mode) && !ExpenseSettings.MODE_SEPARATE.equals(mode)) {
            throw new BadRequestException("Reimbursement mode must be PAYROLL or SEPARATE.");
        }
        ExpenseSettings settings = settingsRepository.findFirstByCompany_IdAndDeletedFalse(companyId)
                .orElseGet(() -> {
                    ExpenseSettings created = new ExpenseSettings();
                    created.setCompany(companyRepository.getReferenceById(companyId));
                    return created;
                });
        settings.setReimbursementMode(mode);
        ExpenseSettings saved = settingsRepository.save(settings);
        auditLogService.log("ExpenseSettings", saved.getId(), "UPDATE", "Expense reimbursement mode set to " + mode);
        return new SettingsView(mode);
    }

    private static void apply(ExpenseCategory category, CategoryRequest request, String name) {
        category.setName(name);
        if (request.active() != null) category.setActive(request.active());
        category.setPerClaimLimit(money(request.perClaimLimit(), "Per-claim limit"));
        category.setReceiptRequiredAbove(nonNegative(request.receiptRequiredAbove(), "Receipt threshold"));
        category.setMileageRatePerKm(money(request.mileageRatePerKm(), "Mileage rate"));
        category.setLimitAction(action(request.limitAction()));
        category.setReceiptAction(action(request.receiptAction()));
    }

    private static String requiredName(CategoryRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new BadRequestException("Category name is required.");
        }
        String name = request.name().trim();
        if (name.length() > 100) throw new BadRequestException("Category name must be 100 characters or fewer.");
        return name;
    }

    private static BigDecimal money(BigDecimal value, String label) {
        if (value == null) return null;
        if (value.signum() <= 0) throw new BadRequestException(label + " must be greater than zero.");
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nonNegative(BigDecimal value, String label) {
        if (value == null) return null;
        if (value.signum() < 0) throw new BadRequestException(label + " can't be negative.");
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String action(String value) {
        if (value == null || value.isBlank()) return ExpenseCategory.ACTION_WARN;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!ExpenseCategory.ACTION_WARN.equals(normalized) && !ExpenseCategory.ACTION_BLOCK.equals(normalized)) {
            throw new BadRequestException("Policy action must be WARN or BLOCK.");
        }
        return normalized;
    }

    static CategoryView toView(ExpenseCategory category) {
        return new CategoryView(category.getId(), category.getCode(), category.getName(), category.isActive(),
                category.getPerClaimLimit(), category.getLimitAction(), category.getReceiptRequiredAbove(),
                category.getReceiptAction(), category.getMileageRatePerKm(), category.getSortOrder());
    }

    private static Long requiredTenant() {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) throw new BadRequestException("A company context is required.");
        return companyId;
    }
}

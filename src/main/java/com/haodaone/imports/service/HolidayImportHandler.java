package com.haodaone.imports.service;

import com.haodaone.attendance.repository.OfficeLocationRepository;
import com.haodaone.imports.entity.DataImportBatch;
import com.haodaone.leave.dto.HolidayDTO;
import com.haodaone.leave.entity.Holiday;
import com.haodaone.leave.repository.HolidayRepository;
import com.haodaone.leave.service.HolidayService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

/**
 * Company holidays through {@link HolidayService#create}: date, name, type (mandatory / optional i.e. restricted)
 * and the office locations it applies to (blank = all). A row that clashes with an existing holiday on the same
 * date for an overlapping location set is skipped, not an error, so the same calendar can be uploaded again safely.
 */
@Component
public class HolidayImportHandler implements ImportHandler {

    static final String DATE = "Date";
    static final String NAME = "Holiday Name";
    static final String TYPE = "Type";
    static final String LOCATIONS = "Locations";

    private static final List<ImportSheet.Column> COLUMNS = List.of(
            new ImportSheet.Column(DATE, true, "Holiday date (YYYY-MM-DD)"),
            new ImportSheet.Column(NAME, true, "Name shown to employees, up to 150 characters"),
            new ImportSheet.Column(TYPE, false, "Mandatory (default) or Optional (restricted holiday employees may pick)"),
            new ImportSheet.Column(LOCATIONS, false, "Office location names separated by commas; blank = all locations"));

    private final HolidayRepository holidayRepository;
    private final HolidayService holidayService;
    private final OfficeLocationRepository officeLocationRepository;

    public HolidayImportHandler(HolidayRepository holidayRepository, HolidayService holidayService,
                                OfficeLocationRepository officeLocationRepository) {
        this.holidayRepository = holidayRepository;
        this.holidayService = holidayService;
        this.officeLocationRepository = officeLocationRepository;
    }

    @Override
    public ImportType type() {
        return ImportType.HOLIDAYS;
    }

    @Override
    public List<ImportSheet.Column> columns() {
        return COLUMNS;
    }

    @Override
    public List<List<String>> exampleRows() {
        int year = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).getYear() + 1;
        return List.of(
                List.of(year + "-01-26", "Republic Day", "Mandatory", ""),
                List.of(year + "-01-15", "Pongal", "Optional", "Chennai Office"));
    }

    @Override
    public List<String> instructions() {
        return List.of(
                "One row per holiday. Holidays are excluded when counting leave days.",
                "Type: Mandatory (everyone it applies to) or Optional (restricted holiday that counts only for employees who pick it).",
                "Locations: exact office location names from Organization Settings, comma separated. Leave blank for all locations.",
                "If a holiday already exists on that date for the same (or overlapping) locations, the row is skipped - existing holidays are never changed.",
                "Requires Leave Manage permission with organization scope.");
    }

    record Payload(LocalDate date, String name, String type, List<Long> locationIds) {
    }

    @Override
    public List<RowPlan> validate(Long companyId, List<ImportSheet.SheetRow> rows, ImportOptions options) {
        List<Holiday> existing = holidayRepository.findAllByCompany_IdAndDateBetweenAndDeletedFalse(companyId,
                LocalDate.of(1900, 1, 1), LocalDate.of(2200, 12, 31));
        Map<String, Long> locationsByName = new HashMap<>();
        officeLocationRepository.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(companyId)
                .forEach(l -> locationsByName.putIfAbsent(l.getName().trim().toLowerCase(Locale.ROOT), l.getId()));
        List<Payload> accepted = new ArrayList<>();
        List<RowPlan> plans = new ArrayList<>();
        for (ImportSheet.SheetRow row : rows) {
            RowPlan plan = new RowPlan(row.number());
            ImportSheet.Reader r = new ImportSheet.Reader(row, plan);
            LocalDate date = r.date(DATE, true);
            String name = r.required(NAME);
            String typeText = r.text(TYPE);
            String locationText = r.text(LOCATIONS);
            if (name != null && name.length() > 150) plan.error("Holiday Name must be at most 150 characters");
            if (date != null && (date.getYear() < 2000 || date.getYear() > 2100)) plan.error("Date must be between 2000 and 2100");
            String type = Holiday.MANDATORY;
            if (typeText != null) {
                String t = typeText.trim().toUpperCase(Locale.ROOT);
                if (t.startsWith("OPT") || t.startsWith("RESTRICT") || t.equals("RH")) type = Holiday.OPTIONAL;
                else if (!t.startsWith("MAND") && !t.equals("GENERAL") && !t.equals("PUBLIC")) plan.error("Type must be Mandatory or Optional");
            }
            Set<Long> locationIds = new LinkedHashSet<>();
            if (locationText != null) {
                for (String part : locationText.split("[,;]")) {
                    String key = part.trim().toLowerCase(Locale.ROOT);
                    if (key.isEmpty()) continue;
                    Long id = locationsByName.get(key);
                    if (id == null) plan.error("Office location '" + part.trim() + "' was not found");
                    else locationIds.add(id);
                }
            }
            if (plan.valid()) {
                Holiday clash = existing.stream()
                        .filter(h -> h.getDate().equals(date) && overlaps(locationIds, h.getLocationIds()))
                        .findFirst().orElse(null);
                boolean inFile = accepted.stream().anyMatch(p -> p.date().equals(date) && overlaps(locationIds, new HashSet<>(p.locationIds())));
                if (inFile) {
                    plan.error("Duplicate row: another row in this file has a holiday on " + date + " for the same location(s)");
                } else if (clash != null) {
                    plan.skip("Skip - '" + clash.getName() + "' already on this date");
                } else {
                    Payload payload = new Payload(date, name, type, List.copyOf(locationIds));
                    accepted.add(payload);
                    plan.action("Add " + type.toLowerCase(Locale.ROOT) + " holiday" + (locationIds.isEmpty() ? " (all locations)" : ""));
                    plan.payload(payload);
                }
            }
            plans.add(plan);
        }
        return plans;
    }

    private static boolean overlaps(Set<Long> a, Set<Long> b) {
        Set<Long> other = b == null ? Set.of() : b;
        return a.isEmpty() || other.isEmpty() || other.stream().anyMatch(a::contains);
    }

    @Override
    public String apply(Long companyId, RowPlan plan, DataImportBatch batch, ImportOptions options) {
        Payload p = plan.payloadAs(Payload.class);
        HolidayDTO.CreateRequest request = new HolidayDTO.CreateRequest();
        request.setDate(p.date());
        request.setName(p.name());
        request.setHolidayType(p.type());
        request.setLocationIds(p.locationIds());
        holidayService.create(request);
        return "Added " + p.name() + " on " + p.date();
    }
}

package com.haodaone.survey;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.performance.suite.PerfContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Surveys, quick polls and eNPS pulses. HR (EMPLOYEE_MANAGE org-wide) builds a survey, publishes it to everyone or one
 * department, and reads aggregated results. Anonymous surveys store no employee id: a keyed hash of survey + employee
 * stops double answers, and results stay hidden until at least {@value #MIN_ANONYMOUS} people have answered.
 */
@Service
public class SurveyService {

    static final int MIN_ANONYMOUS = 3;
    public static final Set<String> KINDS = Set.of("SURVEY", "POLL", "ENPS");
    public static final Set<String> TYPES = Set.of("RATING", "SCALE10", "SINGLE", "MULTI", "TEXT");
    static final String ENPS_QUESTION = "How likely are you to recommend working here to a friend?";

    private final SurveyRepository surveys;
    private final SurveyResponseRepository responses;
    private final PerfContext ctx;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final byte[] keySecret;

    public SurveyService(SurveyRepository surveys, SurveyResponseRepository responses, PerfContext ctx, AuditLogService auditLogService,
                         ObjectMapper objectMapper, Clock applicationClock, @Value("${app.jwt.secret}") String secret) {
        this.surveys = surveys;
        this.responses = responses;
        this.ctx = ctx;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.clock = applicationClock;
        // Derived, purpose-specific key: the JWT secret itself is never used directly or stored.
        this.keySecret = hmac(secret.getBytes(StandardCharsets.UTF_8), "vettri-survey-respondent-v1");
    }

    public record Question(String id, String type, String text, List<String> options, boolean required) { }
    public record SurveyRequest(String title, String description, String kind, Boolean anonymous, LocalDate closesOn, Long departmentId,
                                List<Question> questions, Boolean resultsVisible) { }
    public record SurveyDTO(Long id, String title, String description, String kind, boolean anonymous, String status, LocalDate closesOn,
                            Long departmentId, List<Question> questions, String createdByName, LocalDateTime publishedAt, boolean resultsVisible,
                            long responses, boolean answered) { }
    public record QuestionResult(String id, String type, String text, Long answered, Double average, Map<String, Long> counts,
                                 Integer enps, List<String> comments) { }
    public record Results(Long surveyId, String title, long responses, long audience, boolean hidden, String hiddenReason, List<QuestionResult> questions) { }

    // ------------------------------------------------------------------ HR

    @Transactional(readOnly = true)
    public List<SurveyDTO> list() {
        requireAdmin();
        return surveys.findAllByCompanyIdAndDeletedFalseOrderByCreatedAtDesc(ctx.companyId()).stream().map(s -> toDto(s, false)).toList();
    }

    @Transactional
    public SurveyDTO save(Long id, SurveyRequest r, String actor) {
        requireAdmin();
        Survey s = id == null ? new Survey() : survey(id);
        if (id != null && !Survey.DRAFT.equals(s.getStatus())) throw new BadRequestException("Only drafts can be edited");
        if (r == null) throw new BadRequestException("Describe the survey");
        String kind = r.kind() == null ? "SURVEY" : r.kind().toUpperCase(Locale.ROOT);
        if (!KINDS.contains(kind)) throw new BadRequestException("Unknown survey type");
        String title = PerfContext.text(r.title(), 200);
        if (title == null) throw new BadRequestException("Give it a title");
        List<Question> questions = normalise(kind, r.questions());
        s.setCompanyId(ctx.companyId());
        s.setTitle(title);
        s.setDescription(PerfContext.text(r.description(), 1000));
        s.setKind(kind);
        s.setAnonymous(!"POLL".equals(kind) ? !Boolean.FALSE.equals(r.anonymous()) : Boolean.TRUE.equals(r.anonymous()));
        s.setClosesOn(r.closesOn());
        s.setDepartmentId(r.departmentId());
        s.setQuestionsJson(write(questions));
        s.setResultsVisible(Boolean.TRUE.equals(r.resultsVisible()));
        s.setStatus(Survey.DRAFT);
        s.setCreatedByName(actor);
        s = surveys.save(s);
        return toDto(s, false);
    }

    @Transactional
    public SurveyDTO publish(Long id, String actor) {
        requireAdmin();
        Survey s = survey(id);
        if (!Survey.DRAFT.equals(s.getStatus())) throw new BadRequestException("Already published");
        if (s.getClosesOn() != null && s.getClosesOn().isBefore(LocalDate.now(clock))) throw new BadRequestException("The closing date is in the past");
        s.setStatus(Survey.OPEN);
        s.setPublishedAt(LocalDateTime.now(clock));
        surveys.save(s);
        for (Employee e : audience(s)) {
            ctx.notify(e, "SURVEY", "POLL".equals(s.getKind()) ? "Quick poll: " + s.getTitle() : "Your feedback: " + s.getTitle(),
                    s.isAnonymous() ? "Your answers are anonymous." : "Takes a minute.", "SURVEY", s.getId());
        }
        auditLogService.log("SURVEY", s.getId(), "PUBLISHED", actor + " published '" + s.getTitle() + "'");
        return toDto(s, false);
    }

    @Transactional
    public SurveyDTO close(Long id, String actor) {
        requireAdmin();
        Survey s = survey(id);
        if (!Survey.OPEN.equals(s.getStatus())) throw new BadRequestException("Only open surveys can be closed");
        s.setStatus(Survey.CLOSED);
        surveys.save(s);
        auditLogService.log("SURVEY", s.getId(), "CLOSED", actor + " closed '" + s.getTitle() + "'");
        return toDto(s, false);
    }

    @Transactional
    public void delete(Long id) {
        requireAdmin();
        Survey s = survey(id);
        if (!Survey.DRAFT.equals(s.getStatus())) throw new BadRequestException("Only drafts can be deleted - close it instead");
        s.setDeleted(true);
        surveys.save(s);
    }

    @Transactional(readOnly = true)
    public Results results(Long id) {
        Survey s = survey(id);
        boolean admin = isAdmin();
        if (!admin) {
            Employee me = ctx.me();
            if (!s.isResultsVisible() || !responses.existsBySurveyIdAndRespondentKey(s.getId(), key(s.getId(), me.getId())))
                throw new AccessDeniedException("Results aren't shared for this survey");
        }
        List<SurveyResponse> rows = responses.findAllBySurveyIdAndDeletedFalse(s.getId());
        long audience = audience(s).size();
        if (s.isAnonymous() && rows.size() < MIN_ANONYMOUS) {
            return new Results(s.getId(), s.getTitle(), rows.size(), audience, true,
                    "Results appear once " + MIN_ANONYMOUS + " people have answered, so nobody can be identified.", List.of());
        }
        List<Map<String, Object>> answers = rows.stream().map(this::answers).toList();
        List<QuestionResult> out = new ArrayList<>();
        for (Question q : questions(s)) {
            List<Object> values = answers.stream().map(a -> a.get(q.id())).filter(v -> v != null && !(v instanceof String str && str.isBlank())).toList();
            switch (q.type()) {
                case "RATING", "SCALE10" -> {
                    List<Integer> nums = values.stream().map(v -> ((Number) v).intValue()).toList();
                    Map<String, Long> counts = new TreeMap<>(Comparator.comparingInt(Integer::parseInt));
                    nums.forEach(n -> counts.merge(String.valueOf(n), 1L, Long::sum));
                    Double avg = nums.isEmpty() ? null : Math.round(nums.stream().mapToInt(Integer::intValue).average().orElse(0) * 10) / 10.0;
                    Integer enps = null;
                    if ("SCALE10".equals(q.type()) && !nums.isEmpty()) {
                        long promoters = nums.stream().filter(n -> n >= 9).count();
                        long detractors = nums.stream().filter(n -> n <= 6).count();
                        enps = (int) Math.round((promoters - detractors) * 100.0 / nums.size());
                    }
                    out.add(new QuestionResult(q.id(), q.type(), q.text(), (long) nums.size(), avg, counts, enps, null));
                }
                case "SINGLE", "MULTI" -> {
                    Map<String, Long> counts = new LinkedHashMap<>();
                    q.options().forEach(o -> counts.put(o, 0L));
                    for (Object v : values) {
                        if (v instanceof List<?> list) list.forEach(o -> counts.merge(String.valueOf(o), 1L, Long::sum));
                        else counts.merge(String.valueOf(v), 1L, Long::sum);
                    }
                    out.add(new QuestionResult(q.id(), q.type(), q.text(), (long) values.size(), null, counts, null, null));
                }
                default -> {
                    List<String> comments = new ArrayList<>(values.stream().map(String::valueOf).toList());
                    if (s.isAnonymous()) Collections.shuffle(comments);
                    out.add(new QuestionResult(q.id(), q.type(), q.text(), (long) comments.size(), null, null, null, comments));
                }
            }
        }
        return new Results(s.getId(), s.getTitle(), rows.size(), audience, false, null, out);
    }

    // ------------------------------------------------------------------ employees

    /** Open surveys for me, newest first, with whether I've answered. */
    @Transactional(readOnly = true)
    public List<SurveyDTO> forMe() {
        Employee me = ctx.me();
        return surveys.findAllByCompanyIdAndDeletedFalseOrderByCreatedAtDesc(ctx.companyId()).stream()
                .filter(s -> !Survey.DRAFT.equals(s.getStatus()))
                .filter(s -> s.getDepartmentId() == null || (me.getDepartment() != null && s.getDepartmentId().equals(me.getDepartment().getId())))
                .filter(s -> Survey.OPEN.equals(s.getStatus()) || responses.existsBySurveyIdAndRespondentKey(s.getId(), key(s.getId(), me.getId())))
                .map(s -> toDto(s, true)).toList();
    }

    @Transactional
    public SurveyDTO answer(Long id, Map<String, Object> answers) {
        Employee me = ctx.me();
        Survey s = survey(id);
        if (!Survey.OPEN.equals(s.getStatus()) || (s.getClosesOn() != null && s.getClosesOn().isBefore(LocalDate.now(clock))))
            throw new BadRequestException("This survey is closed");
        if (s.getDepartmentId() != null && (me.getDepartment() == null || !s.getDepartmentId().equals(me.getDepartment().getId())))
            throw new AccessDeniedException("This survey isn't for your department");
        String key = key(s.getId(), me.getId());
        if (responses.existsBySurveyIdAndRespondentKey(s.getId(), key)) throw new BadRequestException("You've already answered");
        Map<String, Object> clean = validate(questions(s), answers == null ? Map.of() : answers);
        SurveyResponse r = new SurveyResponse();
        r.setCompanyId(s.getCompanyId());
        r.setSurveyId(s.getId());
        r.setEmployeeId(s.isAnonymous() ? null : me.getId());
        r.setRespondentKey(key);
        r.setAnswersJson(write(clean));
        responses.save(r);
        return toDto(s, true);
    }

    /** Daily: close surveys whose closing date has passed. */
    @Transactional
    public int closeExpired(LocalDate today) {
        int n = 0;
        for (Survey s : surveys.findAllByStatusAndClosesOnBeforeAndDeletedFalse(Survey.OPEN, today)) {
            s.setStatus(Survey.CLOSED);
            surveys.save(s);
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ helpers

    private List<Question> normalise(String kind, List<Question> requested) {
        List<Question> in = requested == null ? List.of() : requested;
        if ("ENPS".equals(kind)) {
            List<Question> out = new ArrayList<>();
            out.add(new Question("enps", "SCALE10", ENPS_QUESTION, List.of(), true));
            out.add(new Question("why", "TEXT", "What's the main reason for your score?", List.of(), false));
            in.stream().filter(q -> q != null && !"enps".equals(q.id()) && !"why".equals(q.id())).forEach(out::add);
            return validateQuestions(out);
        }
        if ("POLL".equals(kind) && (in.size() != 1 || in.get(0) == null || !Set.of("SINGLE", "MULTI").contains(upper(in.get(0).type()))))
            throw new BadRequestException("A poll has exactly one multiple-choice question");
        return validateQuestions(in);
    }

    private List<Question> validateQuestions(List<Question> in) {
        if (in.isEmpty()) throw new BadRequestException("Add at least one question");
        if (in.size() > 30) throw new BadRequestException("Keep it to 30 questions");
        List<Question> out = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int i = 1;
        for (Question q : in) {
            if (q == null) continue;
            String type = upper(q.type());
            if (!TYPES.contains(type)) throw new BadRequestException("Unknown question type");
            String text = PerfContext.text(q.text(), 300);
            if (text == null) throw new BadRequestException("Every question needs text");
            List<String> options = q.options() == null ? List.of() : q.options().stream().map(o -> PerfContext.text(o, 100)).filter(Objects::nonNull).distinct().toList();
            if (Set.of("SINGLE", "MULTI").contains(type) && (options.size() < 2 || options.size() > 12)) throw new BadRequestException("Choice questions need 2-12 options");
            String id = q.id() == null || q.id().isBlank() || !ids.add(q.id()) ? "q" + i : q.id();
            ids.add(id);
            out.add(new Question(id, type, text, Set.of("SINGLE", "MULTI").contains(type) ? options : List.of(), q.required()));
            i++;
        }
        return out;
    }

    private static Map<String, Object> validate(List<Question> questions, Map<String, Object> answers) {
        Map<String, Object> clean = new LinkedHashMap<>();
        for (Question q : questions) {
            Object v = answers.get(q.id());
            boolean empty = v == null || (v instanceof String s && s.isBlank()) || (v instanceof List<?> l && l.isEmpty());
            if (empty) {
                if (q.required()) throw new BadRequestException("Please answer: " + q.text());
                continue;
            }
            switch (q.type()) {
                case "RATING", "SCALE10" -> {
                    int n;
                    try { n = v instanceof Number num ? num.intValue() : Integer.parseInt(String.valueOf(v)); }
                    catch (NumberFormatException ex) { throw new BadRequestException("Invalid answer for: " + q.text()); }
                    int min = "RATING".equals(q.type()) ? 1 : 0, max = "RATING".equals(q.type()) ? 5 : 10;
                    if (n < min || n > max) throw new BadRequestException("Answer " + min + "-" + max + " for: " + q.text());
                    clean.put(q.id(), n);
                }
                case "SINGLE" -> {
                    if (!q.options().contains(String.valueOf(v))) throw new BadRequestException("Choose one of the options for: " + q.text());
                    clean.put(q.id(), String.valueOf(v));
                }
                case "MULTI" -> {
                    if (!(v instanceof List<?> list)) throw new BadRequestException("Choose options for: " + q.text());
                    List<String> picked = list.stream().map(String::valueOf).distinct().toList();
                    if (!q.options().containsAll(picked)) throw new BadRequestException("Choose from the options for: " + q.text());
                    clean.put(q.id(), picked);
                }
                default -> {
                    String text = String.valueOf(v).trim();
                    if (text.length() > 2000) throw new BadRequestException("Keep answers under 2000 characters");
                    clean.put(q.id(), text);
                }
            }
        }
        return clean;
    }

    private List<Employee> audience(Survey s) {
        return ctx.activeEmployees().stream()
                .filter(e -> s.getDepartmentId() == null || (e.getDepartment() != null && s.getDepartmentId().equals(e.getDepartment().getId()))).toList();
    }

    String key(Long surveyId, Long employeeId) {
        return HexFormat.of().formatHex(hmac(keySecret, surveyId + ":" + employeeId));
    }

    private static byte[] hmac(byte[] secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String upper(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }

    private Map<String, Object> answers(SurveyResponse r) {
        try {
            return objectMapper.readValue(r.getAnswersJson(), new TypeReference<Map<String, Object>>() { });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private List<Question> questions(Survey s) {
        try {
            return objectMapper.readValue(s.getQuestionsJson(), new TypeReference<List<Question>>() { });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String write(Object v) {
        try {
            return objectMapper.writeValueAsString(v);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not save the survey", ex);
        }
    }

    private boolean isAdmin() {
        return ctx.hasOrgScope("EMPLOYEE_MANAGE");
    }

    private void requireAdmin() {
        if (!isAdmin()) throw new AccessDeniedException("Only HR can manage surveys");
    }

    private Survey survey(Long id) {
        return surveys.findByIdAndCompanyIdAndDeletedFalse(id, ctx.companyId()).orElseThrow(() -> new ResourceNotFoundException("Survey not found"));
    }

    private SurveyDTO toDto(Survey s, boolean forMe) {
        Employee me = forMe ? ctx.meOrNull() : null;
        boolean answered = me != null && responses.existsBySurveyIdAndRespondentKey(s.getId(), key(s.getId(), me.getId()));
        return new SurveyDTO(s.getId(), s.getTitle(), s.getDescription(), s.getKind(), s.isAnonymous(), s.getStatus(), s.getClosesOn(), s.getDepartmentId(),
                questions(s), s.getCreatedByName(), s.getPublishedAt(), s.isResultsVisible(), responses.countBySurveyIdAndDeletedFalse(s.getId()), answered);
    }
}

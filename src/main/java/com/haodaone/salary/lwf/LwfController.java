package com.haodaone.salary.lwf;

import com.haodaone.salary.service.PayslipService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;

/** Labour Welfare Fund rules and the monthly LWF return. */
@RestController
@RequestMapping("/api/salary/lwf")
@PreAuthorize("@authorizationService.hasOrganizationScope('SALARY_MANAGE')")
public class LwfController {

    private final LwfService service;

    public LwfController(LwfService service) {
        this.service = service;
    }

    @GetMapping("/rules")
    public List<LwfService.RuleDTO> rules() {
        return service.rules();
    }

    @GetMapping("/states")
    public List<LwfService.StateOption> states() {
        return service.states();
    }

    public record AddState(String stateCode) { }

    @PostMapping("/rules")
    public LwfService.RuleDTO add(@RequestBody AddState body) {
        return service.addState(body == null ? null : body.stateCode());
    }

    @PutMapping("/rules/{id}")
    public LwfService.RuleDTO update(@PathVariable Long id, @RequestBody LwfService.RuleDTO body) {
        return service.update(id, body);
    }

    @GetMapping("/return")
    public LwfService.ReturnSummary returnSummary(@RequestParam String period) {
        return service.returnFor(YearMonth.parse(period));
    }

    @GetMapping("/return.csv")
    public ResponseEntity<byte[]> returnCsv(@RequestParam String period) {
        PayslipService.PayslipFile file = service.returnCsv(YearMonth.parse(period));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(file.filename()).build());
        headers.setCacheControl("no-store");
        return ResponseEntity.ok().headers(headers).body(file.content());
    }
}

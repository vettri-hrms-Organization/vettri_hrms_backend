package com.haodaone.salary.dto;

import com.haodaone.salary.entity.PayrollItem;
import com.haodaone.salary.entity.PayrollRun;

import java.util.List;

public class PayrollRunDTO {

    private PayrollRunSummaryDTO run;
    private List<PayrollItemDTO> items;

    public static PayrollRunDTO of(PayrollRun run, List<PayrollItem> items) {
        PayrollRunDTO dto = new PayrollRunDTO();
        dto.run = PayrollRunSummaryDTO.from(run);
        dto.items = items.stream().map(PayrollItemDTO::from).toList();
        return dto;
    }

    public PayrollRunSummaryDTO getRun() {
        return run;
    }

    public List<PayrollItemDTO> getItems() {
        return items;
    }
}

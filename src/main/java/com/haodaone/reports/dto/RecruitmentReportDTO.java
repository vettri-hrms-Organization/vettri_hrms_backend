package com.haodaone.reports.dto;

import java.util.Map;

public class RecruitmentReportDTO {
    private long openRequisitions;
    private long totalCandidates;
    private Map<String, Long> byStage;
    private long hiredThisYear;
    private Double averageDaysToHire;

    public RecruitmentReportDTO(long openRequisitions, long totalCandidates, Map<String, Long> byStage,
                                 long hiredThisYear, Double averageDaysToHire) {
        this.openRequisitions = openRequisitions;
        this.totalCandidates = totalCandidates;
        this.byStage = byStage;
        this.hiredThisYear = hiredThisYear;
        this.averageDaysToHire = averageDaysToHire;
    }

    public long getOpenRequisitions() {
        return openRequisitions;
    }

    public long getTotalCandidates() {
        return totalCandidates;
    }

    public Map<String, Long> getByStage() {
        return byStage;
    }

    public long getHiredThisYear() {
        return hiredThisYear;
    }

    public Double getAverageDaysToHire() {
        return averageDaysToHire;
    }
}

package com.haodaone.employee.dto;

import java.util.List;

public record EmployeeImportResultDTO(int totalRows, int validRows, int invalidRows,
                                      List<EmployeeImportRowDTO> rows, String message) {
}
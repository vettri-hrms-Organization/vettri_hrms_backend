package com.haodaone.imports.service;

import com.haodaone.common.exception.BadRequestException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Excel plumbing shared by every import (Apache POI): builds the downloadable template (data sheet with bold
 * headers and example rows + an "Instructions" sheet) and reads an uploaded workbook into rows keyed by column
 * header. Columns are matched by header text (case-insensitive, a trailing "*" ignored), so their order does not
 * matter and extra columns are ignored.
 */
public final class ImportSheet {

    public static final int MAX_ROWS = 5000;
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("d.M.uuuu"),
            DateTimeFormatter.ofPattern("d-MMM-uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH));

    private ImportSheet() {
    }

    /** A template column. {@code hint} is shown in the instructions sheet. */
    public record Column(String header, boolean required, String hint) {
        public String label() {
            return required ? header + " *" : header;
        }
    }

    /** One non-blank data row: 1-based Excel row number, text by column header, and dates read from date cells. */
    public record SheetRow(int number, Map<String, String> text, Map<String, LocalDate> cellDates) {
    }

    // ------------------------------------------------------------------ template

    public static byte[] template(String sheetName, List<Column> columns, List<List<String>> examples, List<String> instructions) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            CellStyle bold = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            bold.setFont(font);
            CellStyle text = workbook.createCellStyle();
            text.setDataFormat(workbook.createDataFormat().getFormat("@"));
            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns.get(i).label());
                cell.setCellStyle(bold);
                sheet.setColumnWidth(i, Math.max(16, columns.get(i).label().length() + 4) * 256);
                sheet.setDefaultColumnStyle(i, text);
            }
            for (int r = 0; r < examples.size(); r++) {
                Row row = sheet.createRow(r + 1);
                List<String> values = examples.get(r);
                for (int i = 0; i < values.size() && i < columns.size(); i++) {
                    Cell cell = row.createCell(i);
                    cell.setCellStyle(text);
                    cell.setCellValue(values.get(i));
                }
            }
            sheet.createFreezePane(0, 1);

            Sheet help = workbook.createSheet("Instructions");
            int line = 0;
            Row title = help.createRow(line++);
            title.createCell(0).setCellValue("How to fill this template");
            title.getCell(0).setCellStyle(bold);
            for (String instruction : instructions) help.createRow(line++).createCell(0).setCellValue(instruction);
            line++;
            Row columnsTitle = help.createRow(line++);
            columnsTitle.createCell(0).setCellValue("Columns (* = required)");
            columnsTitle.getCell(0).setCellStyle(bold);
            for (Column column : columns) {
                Row row = help.createRow(line++);
                row.createCell(0).setCellValue(column.label());
                row.createCell(1).setCellValue(column.hint() == null ? "" : column.hint());
            }
            help.createRow(++line).createCell(0).setCellValue(
                    "Delete the example rows before uploading. Do not add a company column - the server uses your workspace.");
            help.setColumnWidth(0, 40 * 256);
            help.setColumnWidth(1, 100 * 256);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BadRequestException("Could not create the Excel template");
        }
    }

    // ------------------------------------------------------------------ reading

    public static List<SheetRow> read(byte[] content, String filename, List<Column> columns) {
        String name = filename == null ? "" : filename;
        if (content == null || content.length == 0 || !name.toLowerCase(Locale.ROOT).matches(".*\\.(xlsx|xls)$")) {
            throw new BadRequestException("Upload an .xlsx or .xls file");
        }
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.ENGLISH);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) throw new BadRequestException("The workbook is empty");
            Map<String, Integer> indexByHeader = new HashMap<>();
            for (Cell cell : header) {
                String key = normalize(formatter.formatCellValue(cell));
                if (!key.isEmpty()) indexByHeader.putIfAbsent(key, cell.getColumnIndex());
            }
            Map<String, Integer> positions = new LinkedHashMap<>();
            List<String> missing = new ArrayList<>();
            for (Column column : columns) {
                Integer index = indexByHeader.get(normalize(column.header()));
                if (index != null) positions.put(column.header(), index);
                else if (column.required()) missing.add(column.header());
            }
            if (!missing.isEmpty()) {
                throw new BadRequestException("This doesn't look like the right template: missing column(s) "
                        + String.join(", ", missing) + ". Download the template and copy your data into it.");
            }
            List<SheetRow> rows = new ArrayList<>();
            for (int i = header.getRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                Map<String, String> text = new LinkedHashMap<>();
                Map<String, LocalDate> dates = new HashMap<>();
                boolean any = false;
                for (Map.Entry<String, Integer> entry : positions.entrySet()) {
                    Cell cell = row.getCell(entry.getValue());
                    String value = cell == null ? "" : formatter.formatCellValue(cell, evaluator).trim();
                    if (cell != null && cell.getCellType() == CellType.NUMERIC) {
                        if (DateUtil.isCellDateFormatted(cell)) {
                            dates.put(entry.getKey(), cell.getLocalDateTimeCellValue().toLocalDate());
                        } else {
                            // Plain digits, never "1.002E+11" (General format) - matters for UAN/account numbers.
                            value = BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
                        }
                    }
                    text.put(entry.getKey(), value);
                    if (!value.isEmpty()) any = true;
                }
                if (!any) continue;
                rows.add(new SheetRow(i + 1, text, dates));
                if (rows.size() > MAX_ROWS) {
                    throw new BadRequestException("A workbook can have at most " + MAX_ROWS + " rows. Split it into smaller files.");
                }
            }
            if (rows.isEmpty()) throw new BadRequestException("The workbook has no data rows");
            return rows;
        } catch (IOException | RuntimeException e) {
            if (e instanceof BadRequestException b) throw b;
            throw new BadRequestException("Could not read the Excel file: " + e.getMessage());
        }
    }

    static String normalize(String header) {
        if (header == null) return "";
        String value = header.trim();
        while (value.endsWith("*")) value = value.substring(0, value.length() - 1).trim();
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    // ------------------------------------------------------------------ field parsing

    /** Reads typed values from a row, recording a readable error on the plan for anything invalid. */
    public static final class Reader {
        private final SheetRow row;
        private final RowPlan plan;

        public Reader(SheetRow row, RowPlan plan) {
            this.row = row;
            this.plan = plan;
        }

        /** Trimmed text, or null when blank. Also recorded as the preview value. */
        public String text(String column) {
            String value = row.text().get(column);
            plan.value(column, value);
            return value == null || value.isBlank() ? null : value.trim();
        }

        public String required(String column) {
            String value = text(column);
            if (value == null) plan.error(column + " is required");
            return value;
        }

        public LocalDate date(String column, boolean required) {
            String value = required ? required(column) : text(column);
            LocalDate cellDate = row.cellDates().get(column);
            if (cellDate != null) {
                plan.value(column, cellDate.toString());
                return cellDate;
            }
            if (value == null) return null;
            LocalDate parsed = parseDate(value);
            if (parsed == null) plan.error(column + " '" + value + "' is not a valid date (use YYYY-MM-DD)");
            else plan.value(column, parsed.toString());
            return parsed;
        }

        /** Money amount (commas and the rupee sign allowed), at most 2 decimals, not negative. Blank = null. */
        public BigDecimal amount(String column, boolean required) {
            String value = required ? required(column) : text(column);
            if (value == null) return null;
            BigDecimal amount = parseNumber(value);
            if (amount == null) {
                plan.error(column + " '" + value + "' is not a valid amount");
                return null;
            }
            if (amount.signum() < 0) {
                plan.error(column + " cannot be negative");
                return null;
            }
            if (amount.compareTo(new BigDecimal("1000000000")) >= 0) {
                plan.error(column + " is too large");
                return null;
            }
            return amount.setScale(2, RoundingMode.HALF_UP);
        }

        public Double number(String column, boolean required) {
            String value = required ? required(column) : text(column);
            if (value == null) return null;
            BigDecimal parsed = parseNumber(value);
            if (parsed == null) {
                plan.error(column + " '" + value + "' is not a valid number");
                return null;
            }
            return parsed.doubleValue();
        }

        public Integer integer(String column, boolean required) {
            String value = required ? required(column) : text(column);
            if (value == null) return null;
            BigDecimal parsed = parseNumber(value);
            if (parsed == null || parsed.stripTrailingZeros().scale() > 0) {
                plan.error(column + " '" + value + "' must be a whole number");
                return null;
            }
            try {
                return parsed.intValueExact();
            } catch (ArithmeticException e) {
                plan.error(column + " '" + value + "' is out of range");
                return null;
            }
        }

        /** Y/Yes/True/1 or N/No/False/0; blank = defaultValue. */
        public Boolean yesNo(String column, Boolean defaultValue) {
            String value = text(column);
            if (value == null) return defaultValue;
            String v = value.trim().toUpperCase(Locale.ROOT);
            if (Set.of("Y", "YES", "TRUE", "1").contains(v)) return true;
            if (Set.of("N", "NO", "FALSE", "0").contains(v)) return false;
            plan.error(column + " must be Y or N");
            return defaultValue;
        }
    }

    public static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(v, format);
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        return null;
    }

    public static BigDecimal parseNumber(String value) {
        if (value == null) return null;
        String cleaned = value.replace(",", "").replace("₹", "").replace("Rs.", "").replace("Rs", "").replace("INR", "").trim();
        if (cleaned.isEmpty()) return null;
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

package com.haodaone.privacy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * "Export all company data": one CSV per table that carries a company_id
 * column, scoped to the caller's company, zipped. Tables are discovered from
 * information_schema so new modules are included automatically.
 *
 * Credentials and secrets never leave: password hashes, token hashes,
 * encrypted secrets, OTP/MFA material and similar columns are dropped, and
 * pure security tables (refresh tokens, MFA, OTP) are skipped entirely.
 */
@Service
public class CompanyDataExportService {

    private static final Logger log = LoggerFactory.getLogger(CompanyDataExportService.class);
    static final int MAX_ROWS_PER_TABLE = 200_000;

    private static final Set<String> SKIPPED_TABLES = Set.of(
            "refresh_token", "password_reset_token", "email_verification_token", "otp_verification",
            "user_mfa", "mfa_recovery_code", "mfa_challenge", "mfa_email_otp", "mfa_trusted_device",
            "token_rotation_history", "company_deletion_request");

    private final JdbcTemplate jdbcTemplate;

    public CompanyDataExportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Tables (lower-case) that have a company_id column, sorted. */
    public List<String> companyTables() {
        List<String> tables = jdbcTemplate.queryForList("""
                SELECT DISTINCT LOWER(c.table_name)
                FROM information_schema.columns c
                JOIN information_schema.tables t
                  ON t.table_name = c.table_name AND t.table_schema = c.table_schema
                WHERE LOWER(c.column_name) = 'company_id'
                  AND LOWER(c.table_schema) = LOWER(CURRENT_SCHEMA)
                  AND UPPER(t.table_type) = 'BASE TABLE'
                ORDER BY 1
                """, String.class);
        List<String> result = new ArrayList<>();
        for (String table : tables) {
            if (table != null && table.matches("[a-z0-9_]+") && !SKIPPED_TABLES.contains(table)) {
                result.add(table);
            }
        }
        return result;
    }

    public byte[] buildZip(Long companyId) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        StringBuilder manifest = new StringBuilder("table,rows,note\r\n");
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (String table : companyTables()) {
                try {
                    // Table names come from information_schema and are validated to [a-z0-9_]+ above.
                    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                            "SELECT * FROM " + table + " WHERE company_id = ? LIMIT " + MAX_ROWS_PER_TABLE, companyId);
                    List<Map<String, Object>> safe = new ArrayList<>(rows.size());
                    for (Map<String, Object> row : rows) {
                        safe.add(stripSecrets(row));
                    }
                    zip.putNextEntry(new ZipEntry(table + ".csv"));
                    zip.write(PersonalDataExportService.CsvWriter.toCsv(safe).getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                    manifest.append(table).append(',').append(rows.size()).append(',')
                            .append(rows.size() >= MAX_ROWS_PER_TABLE ? "truncated - contact support for a full export" : "")
                            .append("\r\n");
                } catch (RuntimeException ex) {
                    log.warn("Company export skipped table {}: {}", table, ex.getMessage());
                    manifest.append(table).append(",0,skipped\r\n");
                }
            }
            zip.putNextEntry(new ZipEntry("_manifest.csv"));
            zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("README.txt"));
            zip.write(("Vettri HRMS company data export\r\nGenerated: " + LocalDateTime.now()
                    + "\r\nOne CSV per data table, limited to your organization. Credentials, token hashes and "
                    + "encrypted secrets are excluded.\r\nUploaded document files are not included; download them "
                    + "from each employee's Documents tab.\r\n").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not build the company export", ex);
        }
        return bytes.toByteArray();
    }

    static boolean isSecretColumn(String column) {
        String c = column.toLowerCase(Locale.ROOT);
        return c.contains("password") || c.contains("token") || c.contains("secret") || c.endsWith("_enc")
                || c.equals("code_hash") || c.equals("salt") || c.contains("api_key") || c.contains("private_key")
                || c.startsWith("encrypted");
    }

    private static Map<String, Object> stripSecrets(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            String key = e.getKey() == null ? "" : e.getKey().toLowerCase(Locale.ROOT);
            if (isSecretColumn(key)) continue;
            Object v = e.getValue();
            out.put(key, v instanceof byte[] ? "[binary]" : v);
        }
        return out;
    }
}

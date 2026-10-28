package com.haodaone.imports.service;

import com.haodaone.imports.entity.DataImportBatch;

import java.util.List;

/** One kind of import: its template and how rows are validated and applied. Implementations are Spring beans. */
public interface ImportHandler {

    ImportType type();

    List<ImportSheet.Column> columns();

    /** Sample rows written below the header in the downloadable template (values in column order). */
    List<List<String>> exampleRows();

    /** Lines written to the template's "Instructions" sheet. */
    List<String> instructions();

    /**
     * Validates every row against the tenant's data (read-only). Returns one plan per row in the same order;
     * a plan with no errors is applied unless it is marked {@link RowPlan#skip}.
     */
    List<RowPlan> validate(Long companyId, List<ImportSheet.SheetRow> rows, ImportOptions options);

    /**
     * Writes one valid row inside the caller's transaction. Throw a BadRequestException (or any runtime
     * exception) to fail the row. Returns a short description of what was done.
     */
    String apply(Long companyId, RowPlan plan, DataImportBatch batch, ImportOptions options);
}

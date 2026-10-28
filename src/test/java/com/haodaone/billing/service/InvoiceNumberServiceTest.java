package com.haodaone.billing.service;

import com.haodaone.billing.entity.InvoiceCounter;
import com.haodaone.billing.repository.InvoiceCounterRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InvoiceNumberServiceTest {

    private final Map<String, InvoiceCounter> rows = new HashMap<>();

    private InvoiceNumberService service() {
        InvoiceCounterRepository repo = mock(InvoiceCounterRepository.class);
        when(repo.findForUpdate(anyString())).thenAnswer(inv -> Optional.ofNullable(rows.get(inv.getArgument(0, String.class))));
        when(repo.save(any(InvoiceCounter.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repo.saveAndFlush(any(InvoiceCounter.class))).thenAnswer(inv -> {
            InvoiceCounter counter = inv.getArgument(0);
            rows.put(counter.getSeries(), counter);
            return counter;
        });
        BillingSettings settings = new BillingSettings("Vettri", "", "", "", 7, 0);
        return new InvoiceNumberService(repo, settings, mock(PlatformTransactionManager.class));
    }

    @Test
    void numbersAreSequentialPerFinancialYearWithoutGaps() {
        InvoiceNumberService numbers = service();
        assertThat(numbers.nextInvoiceNumber(LocalDate.of(2026, 10, 28))).isEqualTo("VTR/2026-27/00001");
        assertThat(numbers.nextInvoiceNumber(LocalDate.of(2026, 11, 2))).isEqualTo("VTR/2026-27/00002");
        assertThat(numbers.nextInvoiceNumber(LocalDate.of(2027, 3, 31))).isEqualTo("VTR/2026-27/00003");
        // New financial year restarts at 1; first use creates the counter row.
        assertThat(numbers.nextInvoiceNumber(LocalDate.of(2027, 4, 1))).isEqualTo("VTR/2027-28/00001");
        assertThat(numbers.nextInvoiceNumber(LocalDate.of(2027, 4, 2))).isEqualTo("VTR/2027-28/00002");
        assertThat(rows.get("INV:2026-27").getLastValue()).isEqualTo(3);
    }

    @Test
    void creditNotesUseTheirOwnSeries() {
        InvoiceNumberService numbers = service();
        numbers.nextInvoiceNumber(LocalDate.of(2026, 10, 28));
        assertThat(numbers.nextCreditNoteNumber(LocalDate.of(2026, 10, 29))).isEqualTo("VTR/CN/2026-27/00001");
        assertThat(numbers.nextInvoiceNumber(LocalDate.of(2026, 10, 30))).isEqualTo("VTR/2026-27/00002");
    }
}

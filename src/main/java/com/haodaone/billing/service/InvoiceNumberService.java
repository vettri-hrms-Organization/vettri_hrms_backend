package com.haodaone.billing.service;

import com.haodaone.billing.entity.InvoiceCounter;
import com.haodaone.billing.gst.FinancialYear;
import com.haodaone.billing.repository.InvoiceCounterRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;

/**
 * Gapless, sequential invoice numbers per financial year:
 * VTR/2026-27/00001 for tax invoices and VTR/CN/2026-27/00001 for credit
 * notes. The counter row is locked (SELECT ... FOR UPDATE) and incremented
 * in the caller's transaction, so the number is only consumed if the invoice
 * is actually committed - a rollback rolls the counter back too.
 */
@Service
public class InvoiceNumberService {

    public static final String INVOICE_SERIES = "INV";
    public static final String CREDIT_NOTE_SERIES = "CN";

    private final InvoiceCounterRepository counters;
    private final BillingSettings settings;
    private final TransactionTemplate requiresNew;

    public InvoiceNumberService(InvoiceCounterRepository counters, BillingSettings settings,
                                PlatformTransactionManager transactionManager) {
        this.counters = counters;
        this.settings = settings;
        this.requiresNew = transactionManager == null ? null : new TransactionTemplate(transactionManager);
        if (this.requiresNew != null) {
            this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String nextInvoiceNumber(LocalDate documentDate) {
        return next(INVOICE_SERIES, documentDate);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String nextCreditNoteNumber(LocalDate documentDate) {
        return next(CREDIT_NOTE_SERIES, documentDate);
    }

    String next(String series, LocalDate documentDate) {
        String fy = FinancialYear.of(documentDate);
        String key = series + ":" + fy;
        InvoiceCounter counter = counters.findForUpdate(key).orElseGet(() -> createAndLock(key));
        long value = counter.getLastValue() + 1;
        counter.setLastValue(value);
        counters.save(counter);
        return format(series, fy, value);
    }

    public String format(String series, String fy, long value) {
        String prefix = settings.invoicePrefix();
        String middle = CREDIT_NOTE_SERIES.equals(series) ? "/CN/" : "/";
        return prefix + middle + fy + "/" + String.format("%05d", value);
    }

    /** First document of a new financial year: create the row in its own transaction, then lock it. */
    private InvoiceCounter createAndLock(String key) {
        try {
            if (requiresNew != null) {
                requiresNew.executeWithoutResult(status -> {
                    InvoiceCounter created = new InvoiceCounter();
                    created.setSeries(key);
                    created.setLastValue(0);
                    counters.saveAndFlush(created);
                });
            }
        } catch (DataIntegrityViolationException concurrentlyCreated) {
            // another node created it first - fine, lock theirs
        }
        return counters.findForUpdate(key)
                .orElseThrow(() -> new IllegalStateException("Invoice counter " + key + " could not be created"));
    }
}

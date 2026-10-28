package com.haodaone.salary.accounting;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

/**
 * Renders a {@link PayrollJournal.Journal} as:
 * <ul>
 *   <li>a journal summary CSV (Ledger, Dr/Cr, Debit, Credit + total row);</li>
 *   <li>Tally ERP 9 / TallyPrime "Import Data" XML: one voucher of the configured type (Journal) with
 *       ALLLEDGERENTRIES.LIST per ledger. Tally sign convention: a DEBIT entry has ISDEEMEDPOSITIVE=Yes and a
 *       NEGATIVE AMOUNT; a CREDIT entry has ISDEEMEDPOSITIVE=No and a POSITIVE AMOUNT, so the amounts of a
 *       balanced voucher sum to zero. Dates are YYYYMMDD. Every ledger must already exist in Tally
 *       (ledger names must match exactly) or the import reports an error for the voucher;</li>
 *   <li>a Zoho Books manual-journal import CSV (Journal Date yyyy-MM-dd, Reference Number, Notes, Journal Type,
 *       Currency, Account, Description, Debit, Credit - one row per account line, rows sharing a reference are one
 *       journal). Map the columns on Zoho's import screen if your template differs.</li>
 * </ul>
 */
public final class AccountingExportFormats {

    private static final DateTimeFormatter TALLY_DATE = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private AccountingExportFormats() {
    }

    public static byte[] journalCsv(PayrollJournal.Journal j) {
        StringBuilder sb = new StringBuilder();
        sb.append("Date,Reference,Ledger,Dr/Cr,Debit,Credit,Narration\n");
        for (PayrollJournal.Entry e : j.entries()) {
            sb.append(ISO.format(j.date())).append(',').append(csv(j.reference())).append(',').append(csv(e.ledger())).append(',')
                    .append(e.isDebit() ? "Dr" : "Cr").append(',')
                    .append(e.isDebit() ? e.debit().toPlainString() : "").append(',')
                    .append(e.isDebit() ? "" : e.credit().toPlainString()).append(',')
                    .append(csv(j.narration())).append('\n');
        }
        sb.append(",,Total,,").append(j.totalDebit().setScale(2).toPlainString()).append(',')
                .append(j.totalCredit().setScale(2).toPlainString()).append(",\n");
        return withBom(sb.toString());
    }

    public static byte[] zohoCsv(PayrollJournal.Journal j) {
        StringBuilder sb = new StringBuilder();
        sb.append("Journal Date,Reference Number,Notes,Journal Type,Currency,Account,Description,Debit,Credit\n");
        for (PayrollJournal.Entry e : j.entries()) {
            sb.append(ISO.format(j.date())).append(',').append(csv(j.reference())).append(',').append(csv(j.narration())).append(',')
                    .append("both,INR,").append(csv(e.ledger())).append(',').append(csv(j.narration())).append(',')
                    .append(e.isDebit() ? e.debit().toPlainString() : "").append(',')
                    .append(e.isDebit() ? "" : e.credit().toPlainString()).append('\n');
        }
        return withBom(sb.toString());
    }

    public static byte[] tallyXml(PayrollJournal.Journal j, String voucherType, String tallyCompanyName) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            Document doc = factory.newDocumentBuilder().newDocument();
            Element envelope = doc.createElement("ENVELOPE");
            doc.appendChild(envelope);
            Element header = child(doc, envelope, "HEADER");
            child(doc, header, "TALLYREQUEST", "Import Data");
            Element body = child(doc, envelope, "BODY");
            Element importData = child(doc, body, "IMPORTDATA");
            Element desc = child(doc, importData, "REQUESTDESC");
            child(doc, desc, "REPORTNAME", "Vouchers");
            if (tallyCompanyName != null && !tallyCompanyName.isBlank()) {
                Element vars = child(doc, desc, "STATICVARIABLES");
                child(doc, vars, "SVCURRENTCOMPANY", tallyCompanyName.trim());
            }
            Element data = child(doc, importData, "REQUESTDATA");
            Element message = child(doc, data, "TALLYMESSAGE");
            message.setAttribute("xmlns:UDF", "TallyUDF");
            String type = voucherType == null || voucherType.isBlank() ? "Journal" : voucherType.trim();
            Element voucher = child(doc, message, "VOUCHER");
            voucher.setAttribute("VCHTYPE", type);
            voucher.setAttribute("ACTION", "Create");
            voucher.setAttribute("OBJVIEW", "Accounting Voucher View");
            String date = TALLY_DATE.format(j.date());
            child(doc, voucher, "DATE", date);
            child(doc, voucher, "EFFECTIVEDATE", date);
            child(doc, voucher, "VOUCHERTYPENAME", type);
            child(doc, voucher, "REFERENCE", j.reference());
            child(doc, voucher, "NARRATION", j.narration());
            child(doc, voucher, "PERSISTEDVIEW", "Accounting Voucher View");
            for (PayrollJournal.Entry e : j.entries()) {
                Element entry = child(doc, voucher, "ALLLEDGERENTRIES.LIST");
                child(doc, entry, "LEDGERNAME", e.ledger());
                child(doc, entry, "ISDEEMEDPOSITIVE", e.isDebit() ? "Yes" : "No");
                BigDecimal amount = e.isDebit() ? e.debit().negate() : e.credit();
                child(doc, entry, "AMOUNT", amount.setScale(2).toPlainString());
            }
            Transformer t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            t.setOutputProperty(OutputKeys.INDENT, "yes");
            t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            StringWriter writer = new StringWriter();
            t.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to build Tally XML", ex);
        }
    }

    private static Element child(Document doc, Element parent, String name) {
        Element el = doc.createElement(name);
        parent.appendChild(el);
        return el;
    }

    private static Element child(Document doc, Element parent, String name, String text) {
        Element el = child(doc, parent, name);
        el.setTextContent(text == null ? "" : text);
        return el;
    }

    static String csv(String value) {
        if (value == null) return "";
        String v = value;
        // Neutralise spreadsheet formula injection.
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private static byte[] withBom(String s) {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = s.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, out, 0, bom.length);
        System.arraycopy(body, 0, out, bom.length, body.length);
        return out;
    }
}

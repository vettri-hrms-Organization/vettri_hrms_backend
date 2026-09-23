package com.haodaone.recruitment.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Shared, email-client-safe presentation shell for transactional messages. */
@Service
public class EmailTemplateService {

    private static final String NAVY = "#0F1B3D";
    private static final String BLUE = "#2563EB";
    private static final String MUTED = "#5F6F86";
    private static final String BORDER = "#E2E8F0";
    private static final String SUPPORT = "customersupport@vettrihrms.in";
    private static final String WEBSITE = "https://www.vettrihrms.in";

    @Value("${app.frontend-url:https://app.vettrihrms.in}")
    private String frontendUrl;

    public String render(String subject, String content) {
        String appUrl = safeUrl(frontendUrl);
        return "<!doctype html>"
                + "<html lang=\"en\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\">"
                + "<title>" + escape(subject) + "</title></head>"
                + "<body style=\"margin:0;padding:0;background:#F4F7FB;color:#17212B;font-family:Arial,Helvetica,sans-serif;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"background:#F4F7FB;\"><tr><td align=\"center\" style=\"padding:28px 12px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"max-width:620px;\">"
                + "<tr><td style=\"padding:8px 8px 22px;\"><span style=\"font-size:21px;font-weight:800;letter-spacing:.02em;color:" + NAVY + ";\">VETTRI</span>"
                + "<span style=\"display:block;margin-top:3px;font-size:11px;letter-spacing:.12em;color:" + MUTED + ";\">HRMS</span></td></tr>"
                + "<tr><td style=\"background:#FFFFFF;border:1px solid " + BORDER + ";border-radius:12px;padding:34px 34px 30px;box-shadow:0 5px 18px rgba(15,27,61,.06);\">"
                + statusStrip(subject)
                + "<h1 style=\"margin:18px 0 22px;color:" + NAVY + ";font-size:28px;line-height:1.2;letter-spacing:-.02em;\">" + escape(subject) + "</h1>"
                + content
                + "</td></tr>"
                + "<tr><td style=\"padding:22px 8px 0;text-align:center;color:" + MUTED + ";font-size:12px;line-height:1.6;\">"
                + "Need help? <a href=\"mailto:" + SUPPORT + "\" style=\"color:" + BLUE + ";text-decoration:none;\">" + SUPPORT + "</a><br>"
                + "<a href=\"" + WEBSITE + "\" style=\"color:" + MUTED + ";text-decoration:none;\">Vettri HRMS</a> &middot; <a href=\"" + appUrl + "\" style=\"color:" + MUTED + ";text-decoration:none;\">Open application</a><br>"
                + "Built by Brothers. Built for Better Workplaces.<br>&copy; 2026 Vettri HRMS. All rights reserved."
                + "</td></tr></table></td></tr></table></body></html>";
    }

        private String statusStrip(String subject) {
        String lower = subject == null ? "" : subject.toLowerCase(java.util.Locale.ROOT);
        String color = lower.contains("failed") || lower.contains("action required") ? "#B42318"
            : lower.contains("successful") || lower.contains("welcome") || lower.contains("ready") ? "#16794C"
            : BLUE;
        String label = lower.contains("failed") || lower.contains("action required") ? "Action required"
            : lower.contains("successful") || lower.contains("welcome") || lower.contains("ready") ? "Vettri HRMS update"
            : "Vettri HRMS notification";
        return "<p style=\"margin:0;color:" + color + ";font-size:11px;font-weight:700;letter-spacing:.1em;text-transform:uppercase;\">"
            + "<span style=\"display:inline-block;width:7px;height:7px;background:" + color + ";border-radius:50%;margin-right:7px;\"></span>"
            + label + "</p>";
        }

    private String safeUrl(String value) {
        if (value == null || value.isBlank() || value.contains(",")
                || !(value.startsWith("http://") || value.startsWith("https://"))) {
            return "https://app.vettrihrms.in";
        }
        return escape(value.replaceAll("/+$", ""));
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}

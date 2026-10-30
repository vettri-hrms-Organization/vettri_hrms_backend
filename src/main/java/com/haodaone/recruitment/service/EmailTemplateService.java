package com.haodaone.recruitment.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Shared, email-client-safe presentation shell for every transactional message: branded header,
 * white content card and a footer that says who sent it and why. Table-based with inline styles so
 * it renders in Gmail, Outlook and Apple Mail; gradients degrade to a solid navy where unsupported.
 */
@Service
public class EmailTemplateService {

    private static final String NAVY = "#10172A";
    private static final String INK = "#1B2850";
    private static final String BLUE = "#4169E1";
    private static final String TEXT = "#334155";
    private static final String MUTED = "#64748B";
    private static final String BORDER = "#E3E8F5";
    private static final String CANVAS = "#EEF1F8";
    private static final String FONT = "'Segoe UI',Roboto,Helvetica,Arial,sans-serif";
    private static final String SUPPORT = "customersupport@vettrihrms.in";
    private static final String WEBSITE = "https://vettrihrms.in";

    @Value("${app.frontend-url:https://app.vettrihrms.in}")
    private String frontendUrl;

    public String render(String subject, String content) {
        String appUrl = safeUrl(frontendUrl);
        Tone tone = tone(subject);
        return "<!doctype html>"
                + "<html lang=\"en\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\">"
                + "<meta name=\"color-scheme\" content=\"light\"><meta name=\"supported-color-schemes\" content=\"light\">"
                + "<title>" + escape(subject) + "</title></head>"
                + "<body style=\"margin:0;padding:0;background:" + CANVAS + ";color:" + TEXT + ";font-family:" + FONT + ";-webkit-font-smoothing:antialiased;\">"
                // Inbox preview line (hidden in the message body).
                + "<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;\">" + escape(subject) + " - Vettri HRMS</div>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"background:" + CANVAS + ";\"><tr><td align=\"center\" style=\"padding:32px 12px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"max-width:600px;\">"

                // Header band: logo on the brand gradient.
                + "<tr><td bgcolor=\"" + INK + "\" style=\"background:" + INK + ";background-image:linear-gradient(120deg," + INK + " 0%,#3457C9 60%,#6558F5 100%);"
                + "border-radius:16px 16px 0 0;padding:26px 32px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>"
                + "<td valign=\"middle\"><a href=\"" + WEBSITE + "\" style=\"text-decoration:none;\">"
                + "<img src=\"" + appUrl + "/brand/vettri-logo-full-transparent.png\" width=\"128\" alt=\"Vettri HRMS\" "
                + "style=\"display:block;border:0;width:128px;height:auto;color:#FFFFFF;font-size:22px;font-weight:800;font-family:" + FONT + ";\"></a></td>"
                + "<td valign=\"middle\" align=\"right\" style=\"font-family:" + FONT + ";font-size:11px;font-weight:700;letter-spacing:.12em;text-transform:uppercase;color:#DCE3FF;\">"
                + "<span style=\"display:inline-block;padding:6px 12px;border-radius:999px;background:rgba(255,255,255,.14);\">"
                + "<span style=\"display:inline-block;width:7px;height:7px;border-radius:50%;background:" + tone.dot + ";margin-right:6px;\"></span>"
                + tone.label + "</span></td>"
                + "</tr></table></td></tr>"

                // Content card.
                + "<tr><td bgcolor=\"#FFFFFF\" style=\"background:#FFFFFF;border:1px solid " + BORDER + ";border-top:0;border-radius:0 0 16px 16px;padding:36px 36px 32px;\">"
                + "<h1 style=\"margin:0 0 20px;color:" + NAVY + ";font-family:" + FONT + ";font-size:24px;line-height:1.3;font-weight:800;letter-spacing:-.01em;\">"
                + escape(subject) + "</h1>"
                // No sign-off here: some messages (offer letters, interview invitations) are sent on a
                // customer company's behalf, so each email body carries its own closing.
                + "<div style=\"font-family:" + FONT + ";font-size:15px;line-height:1.65;color:" + TEXT + ";\">" + content + "</div>"
                + "</td></tr>"

                // Footer.
                + "<tr><td style=\"padding:24px 16px 0;text-align:center;font-family:" + FONT + ";color:" + MUTED + ";font-size:12px;line-height:1.7;\">"
                + "Questions? Reply to this email or write to "
                + "<a href=\"mailto:" + SUPPORT + "\" style=\"color:" + BLUE + ";text-decoration:none;font-weight:600;\">" + SUPPORT + "</a><br>"
                + "<a href=\"" + appUrl + "\" style=\"color:" + BLUE + ";text-decoration:none;\">Open Vettri HRMS</a>"
                + " &nbsp;&middot;&nbsp; <a href=\"" + WEBSITE + "\" style=\"color:" + MUTED + ";text-decoration:none;\">vettrihrms.in</a>"
                + " &nbsp;&middot;&nbsp; <a href=\"" + WEBSITE + "/privacy\" style=\"color:" + MUTED + ";text-decoration:none;\">Privacy</a><br>"
                + "<span style=\"color:#94A3B8;\">You received this email because of your account or activity on Vettri HRMS.</span><br>"
                + "<span style=\"color:#94A3B8;\">Built by Brothers. Built for Better Workplaces. &copy; 2026 Vettri HRMS.</span>"
                + "</td></tr></table></td></tr></table></body></html>";
    }

    private record Tone(String label, String dot) { }

    private Tone tone(String subject) {
        String lower = subject == null ? "" : subject.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("failed") || lower.contains("action required") || lower.contains("suspend") || lower.contains("read-only")) {
            return new Tone("Action required", "#F97066");
        }
        if (lower.contains("welcome") || lower.contains("is ready")) {
            return new Tone("Welcome", "#4ADE80");
        }
        if (lower.contains("payment") || lower.contains("invoice") || lower.contains("receipt") || lower.contains("renew")
                || lower.contains("trial") || lower.contains("subscription")) {
            return new Tone("Billing", "#FDB022");
        }
        if (lower.contains("password") || lower.contains("verify") || lower.contains("code") || lower.contains("sign-in")
                || lower.contains("security") || lower.contains("token")) {
            return new Tone("Security", "#67E8F9");
        }
        return new Tone("Notification", "#A5B4FC");
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

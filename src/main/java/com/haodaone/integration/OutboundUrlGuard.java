package com.haodaone.integration;

import com.haodaone.common.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Stops outbound calls (Slack/Teams posts, customer webhooks) from reaching internal infrastructure (SSRF):
 * HTTPS only, default port, no credentials in the URL, and every address the host resolves to must be public.
 * Checked when a URL is saved and again before every delivery (DNS can change).
 */
@Component
public class OutboundUrlGuard {

    public enum Kind { SLACK, TEAMS, WEBHOOK }

    public URI check(String url, Kind kind) {
        URI uri;
        try {
            uri = URI.create(url == null ? "" : url.trim());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("That isn't a valid URL");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) throw new BadRequestException("Use an https:// URL");
        if (uri.getUserInfo() != null) throw new BadRequestException("Remove the username/password from the URL");
        if (uri.getPort() != -1 && uri.getPort() != 443) throw new BadRequestException("Use the standard https port");
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        switch (kind) {
            case SLACK -> {
                if (!host.equals("hooks.slack.com")) throw new BadRequestException("Paste a Slack incoming-webhook URL (https://hooks.slack.com/...)");
            }
            case TEAMS -> {
                if (!(host.endsWith(".webhook.office.com") || host.endsWith(".logic.azure.com") || host.endsWith(".environment.api.powerplatform.com")))
                    throw new BadRequestException("Paste a Microsoft Teams incoming-webhook or Workflows URL");
            }
            default -> { }
        }
        requirePublic(host);
        return uri;
    }

    private static void requirePublic(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException ex) {
            throw new BadRequestException("Can't find " + host);
        }
        for (InetAddress a : addresses) {
            if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress() || a.isMulticastAddress()
                    || isUniqueLocalV6(a) || isCarrierGradeNat(a)) {
                throw new BadRequestException("That address points to a private network and can't be used");
            }
        }
    }

    private static boolean isUniqueLocalV6(InetAddress a) {
        byte[] b = a.getAddress();
        return b.length == 16 && (b[0] & 0xfe) == 0xfc;
    }

    private static boolean isCarrierGradeNat(InetAddress a) {
        byte[] b = a.getAddress();
        return b.length == 4 && (b[0] & 0xff) == 100 && (b[1] & 0xc0) == 64;
    }
}

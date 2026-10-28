package com.haodaone.notifications.whatsapp;

import java.util.List;

/** Sends an approved template message; returns the provider message id. Throws on failure. */
public interface WhatsAppCloudClient {

    String sendTemplate(String phoneE164, String templateName, String languageCode, List<String> bodyParameters);
}

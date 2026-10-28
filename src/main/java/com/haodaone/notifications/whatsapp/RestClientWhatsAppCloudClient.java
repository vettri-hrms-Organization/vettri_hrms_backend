package com.haodaone.notifications.whatsapp;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Meta WhatsApp Cloud API: POST {base}/{phone-number-id}/messages with a template payload. */
@Component
public class RestClientWhatsAppCloudClient implements WhatsAppCloudClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };
    private final WhatsAppProperties properties;
    private final RestClient restClient;

    public RestClientWhatsAppCloudClient(WhatsAppProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(15_000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String sendTemplate(String phoneE164, String templateName, String languageCode, List<String> bodyParameters) {
        List<Map<String, Object>> parameters = new ArrayList<>();
        for (String value : bodyParameters) {
            parameters.add(Map.of("type", "text", "text", value == null ? "" : value));
        }
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", templateName);
        template.put("language", Map.of("code", languageCode));
        template.put("components", List.of(Map.of("type", "body", "parameters", parameters)));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", phoneE164.startsWith("+") ? phoneE164.substring(1) : phoneE164);
        payload.put("type", "template");
        payload.put("template", template);

        Map<String, Object> response = restClient.post()
                .uri(properties.apiBaseUrl() + "/" + properties.phoneNumberId() + "/messages")
                .header("Authorization", "Bearer " + properties.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(MAP);
        if (response != null && response.get("messages") instanceof List<?> messages && !messages.isEmpty()
                && messages.get(0) instanceof Map<?, ?> first && first.get("id") != null) {
            return String.valueOf(first.get("id"));
        }
        return null;
    }
}

package com.haodaone.sso.service;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** Server-side calls to Google / Microsoft (token exchange and JWKS) through Spring's RestClient. */
@Component
public class RestClientOidcHttpClient implements OidcHttpClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };
    private final RestClient restClient;

    public RestClientOidcHttpClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(15_000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public Map<String, Object> exchangeCode(String tokenEndpoint, Map<String, String> form) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        form.forEach(body::add);
        return restClient.post().uri(tokenEndpoint)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(MAP);
    }

    @Override
    public Map<String, Object> fetchJwks(String jwksUri) {
        return restClient.get().uri(jwksUri).accept(MediaType.APPLICATION_JSON).retrieve().body(MAP);
    }
}

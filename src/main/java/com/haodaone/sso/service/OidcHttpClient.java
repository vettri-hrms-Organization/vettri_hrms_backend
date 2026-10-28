package com.haodaone.sso.service;

import java.util.Map;

/** HTTP calls to the identity provider (an interface so tests can stub them). */
public interface OidcHttpClient {

    /** POSTs the authorization-code grant form to the token endpoint; returns the JSON body. */
    Map<String, Object> exchangeCode(String tokenEndpoint, Map<String, String> form);

    /** GETs the JWKS document. */
    Map<String, Object> fetchJwks(String jwksUri);
}

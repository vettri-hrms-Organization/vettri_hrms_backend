package com.haodaone.attendance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.attendance.dto.GeocodingResultDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GeocodingService {
    private static final long CACHE_TTL_MILLIS = Duration.ofMinutes(10).toMillis();
    private static final int MAX_CACHE_ENTRIES = 256;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String providerUrl;
    private final String userAgent;
    private final Map<String, CachedResult> cache = new ConcurrentHashMap<>();

    public GeocodingService(ObjectMapper objectMapper,
                            @Value("${app.geocoding.provider-url:https://nominatim.openstreetmap.org}") String providerUrl,
                            @Value("${app.geocoding.user-agent:Vettri-HRMS/1.0}") String userAgent) {
        this.objectMapper = objectMapper;
        this.providerUrl = providerUrl.replaceAll("/$", "");
        this.userAgent = userAgent;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public List<GeocodingResultDTO> search(String query) {
        String cacheKey = "search:" + query.trim().toLowerCase();
        CachedResult cached = cache.get(cacheKey);
        if (cached != null && !cached.isExpired()) return cached.results();
        String url = providerUrl + "/search?format=jsonv2&addressdetails=1&limit=5&q=" + encode(query);
        List<GeocodingResultDTO> results = get(url, true).stream().map(this::mapResult).toList();
        putCache(cacheKey, results);
        return results;
    }

    public GeocodingResultDTO reverse(double latitude, double longitude) {
        String cacheKey = "reverse:" + latitude + ":" + longitude;
        CachedResult cached = cache.get(cacheKey);
        if (cached != null && !cached.isExpired()) return cached.results().isEmpty() ? null : cached.results().get(0);
        String url = providerUrl + "/reverse?format=jsonv2&addressdetails=1&lat=" + latitude + "&lon=" + longitude;
        List<JsonNode> results = get(url, false);
        GeocodingResultDTO result = results.isEmpty() ? null : mapResult(results.get(0));
        putCache(cacheKey, result == null ? List.of() : List.of(result));
        return result;
    }

    private void putCache(String key, List<GeocodingResultDTO> results) {
        if (cache.size() >= MAX_CACHE_ENTRIES) cache.clear();
        cache.put(key, new CachedResult(results, System.currentTimeMillis() + CACHE_TTL_MILLIS));
    }

    private record CachedResult(List<GeocodingResultDTO> results, long expiresAt) {
        private boolean isExpired() {
            return System.currentTimeMillis() >= expiresAt;
        }
    }

    private List<JsonNode> get(String url, boolean listResponse) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Location provider rate limit reached.");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Location provider unavailable.");
            }
            JsonNode root = objectMapper.readTree(response.body());
            List<JsonNode> results = new ArrayList<>();
            if (listResponse && root.isArray()) {
                root.forEach(results::add);
            } else if (!listResponse && !root.isMissingNode() && !root.isNull()) {
                results.add(root);
            }
            return results;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Location provider unavailable.");
        } catch (IOException | RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Location provider unavailable.");
        }
    }

    private GeocodingResultDTO mapResult(JsonNode result) {
        JsonNode address = result.path("address");
        String city = firstText(address, "city", "town", "village", "municipality", "county");
        return new GeocodingResultDTO(
                text(result, "display_name"),
                text(result, "display_name"),
                city,
                text(address, "state"),
                text(address, "country"),
                result.path("lat").asDouble(),
                result.path("lon").asDouble()
        );
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

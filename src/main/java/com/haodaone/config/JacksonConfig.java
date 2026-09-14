package com.haodaone.config;

import com.fasterxml.jackson.databind.MapperFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ROOT-CAUSE FIX (see AgentIngestService#recordActivityBatch javadoc for the
 * full incident writeup): HaodaOne.Agent/Services/ApiClientService.cs
 * serializes its C# request models with System.Text.Json's *actual*
 * default, which is PropertyNamingPolicy = null - i.e. it preserves the C#
 * property casing (PascalCase: "Device", "Sessions", "SessionId", ...). The
 * comment that used to live on DeviceInfoPayload claimed System.Text.Json
 * "defaults to camelCase" - it does not; that only happens if the agent
 * explicitly sets JsonNamingPolicy.CamelCase, which it does not do
 * consistently across every request model.
 *
 * Jackson's default deserialization is case-SENSITIVE and, per Spring
 * Boot's default (FAIL_ON_UNKNOWN_PROPERTIES=false), silently DROPS any
 * JSON property it can't match instead of erroring - so a batch whose JSON
 * looks like {"Device": {...}, "Sessions": [...]} against a DTO with fields
 * "device"/"sessions" deserializes to device=<bound if names happen to
 * match elsewhere> and sessions=<the DTO's default, List.of()>, with ZERO
 * exception raised anywhere. That is exactly how the agent got
 * "HTTP 200 + activity flushed on the client" with 0 rows ever reaching
 * activity_session: the request body parsed "successfully", just into an
 * empty sessions list, so AgentIngestService's for-loop never executed.
 *
 * This customizer makes Jackson tolerate either casing for EVERY request
 * DTO in the app (agent payloads and everything else), so a future casing
 * drift in either direction can't silently zero out a request body again.
 * It is deliberately paired with explicit @JsonAlias annotations on the
 * agent DTOs (ActivityBatchRequest, ActivitySessionPayload,
 * DeviceInfoPayload, HeartbeatRequest) rather than relied on alone -
 * ACCEPT_CASE_INSENSITIVE_PROPERTIES only matches property NAMES
 * case-insensitively, it does not help if the agent ever renames a field
 * outright, which @JsonAlias defends against too.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer caseInsensitiveJacksonCustomizer() {
        return builder -> builder.featuresToEnable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES);
    }
}

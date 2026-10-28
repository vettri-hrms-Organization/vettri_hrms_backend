package com.haodaone.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.haodaone.user.dto.UserDTO;

import java.util.List;

/**
 * Normal login: accessToken, refreshToken, tokenType, user (unchanged shape).
 * Second factor pending: mfaRequired=true, mfaToken, methods, mfaExpiresInSeconds
 * and no tokens. deviceToken is only present after an MFA verification with
 * "remember this device" ticked. The MFA-only fields are omitted when null so
 * the normal response is byte-for-byte what existing clients expect.
 */
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private UserDTO user;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean mfaRequired;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String mfaToken;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<String> methods;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer mfaExpiresInSeconds;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String maskedEmail;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String deviceToken;

    public LoginResponse(String accessToken, String refreshToken, UserDTO user) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.user = user;
    }

    private LoginResponse() {
    }

    public static LoginResponse mfaChallenge(String mfaToken, List<String> methods, int expiresInSeconds, String maskedEmail) {
        LoginResponse response = new LoginResponse();
        response.mfaRequired = Boolean.TRUE;
        response.mfaToken = mfaToken;
        response.methods = methods;
        response.mfaExpiresInSeconds = expiresInSeconds;
        response.maskedEmail = maskedEmail;
        response.tokenType = null;
        return response;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public UserDTO getUser() {
        return user;
    }

    public Boolean getMfaRequired() {
        return mfaRequired;
    }

    public String getMfaToken() {
        return mfaToken;
    }

    public List<String> getMethods() {
        return methods;
    }

    public Integer getMfaExpiresInSeconds() {
        return mfaExpiresInSeconds;
    }

    public String getMaskedEmail() {
        return maskedEmail;
    }

    public String getDeviceToken() {
        return deviceToken;
    }

    public void setDeviceToken(String deviceToken) {
        this.deviceToken = deviceToken;
    }
}

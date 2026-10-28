package com.haodaone.common.exception;

/**
 * Password login refused because the company requires single sign-on. Only thrown after the
 * password was verified, so unlike other authentication failures its message is safe to show.
 */
public class SsoRequiredException extends AuthenticationFailedException {
    public SsoRequiredException(String message) {
        super(message);
    }
}

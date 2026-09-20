package com.haodaone.auth.service;

import com.haodaone.auth.entity.EmailVerificationToken;
import com.haodaone.auth.repository.EmailVerificationTokenRepository;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationServiceTest {

    @Mock
    private EmailVerificationTokenRepository tokenRepository;

    @Mock
    private UserRepository userRepository;

    @Test
    void createsHashedExpiringToken() {
        VerificationService service = new VerificationService(tokenRepository, userRepository);
        User user = new User();
        user.setId(42L);

        String rawToken = service.createToken(user);

        assertTrue(rawToken.length() >= 80);
        ArgumentCaptor<EmailVerificationToken> captor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepository).save(captor.capture());
        assertNotEquals(rawToken, captor.getValue().getTokenHash());
        assertEquals(user, captor.getValue().getUser());
        assertTrue(captor.getValue().getExpiresAt().isAfter(LocalDateTime.now()));
    }

    @Test
    void verifiesTokenOnceAndRepeatedClickIsSafe() {
        VerificationService service = new VerificationService(tokenRepository, userRepository);
        User user = new User();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().plusHours(1));
        when(tokenRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(token));

        assertEquals("VERIFIED", service.verify("secure-token"));
        assertTrue(user.isEmailVerified());
        verify(userRepository).save(user);

        assertEquals("ALREADY_VERIFIED", service.verify("secure-token"));
    }

    @Test
    void rejectsUnknownTokenWithoutExposingUserData() {
        VerificationService service = new VerificationService(tokenRepository, userRepository);
        when(tokenRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.empty());

        BadRequestException exception = assertThrows(BadRequestException.class, () -> service.verify("unknown-token"));

        assertEquals("This verification link is invalid or expired.", exception.getMessage());
    }
}

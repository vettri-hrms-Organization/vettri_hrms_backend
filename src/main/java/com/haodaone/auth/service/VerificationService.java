package com.haodaone.auth.service;

import com.haodaone.auth.entity.EmailVerificationToken;
import com.haodaone.auth.repository.EmailVerificationTokenRepository;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class VerificationService {

    private static final int TOKEN_BYTES = 64;
    private static final int EXPIRY_HOURS = 24;

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public VerificationService(EmailVerificationTokenRepository tokenRepository, UserRepository userRepository) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public String createToken(User user) {
        tokenRepository.invalidateUnusedForUser(user.getId());

        byte[] tokenBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(LocalDateTime.now().plusHours(EXPIRY_HOURS));
        tokenRepository.save(token);
        return rawToken;
    }

    @Transactional
    public String verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidToken();
        }

        EmailVerificationToken token = tokenRepository.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(this::invalidToken);
        User user = token.getUser();

        if (user.isEmailVerified()) {
            return "ALREADY_VERIFIED";
        }
        if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(LocalDateTime.now())) {
            if (token.getUsedAt() == null) {
                token.setUsedAt(LocalDateTime.now());
                tokenRepository.save(token);
            }
            throw invalidToken();
        }

        user.setEmailVerified(true);
        user.setEmailVerifiedAt(LocalDateTime.now());
        userRepository.save(user);

        token.setUsedAt(LocalDateTime.now());
        tokenRepository.save(token);
        return "VERIFIED";
    }

    private BadRequestException invalidToken() {
        return new BadRequestException("This verification link is invalid or expired.");
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }
}

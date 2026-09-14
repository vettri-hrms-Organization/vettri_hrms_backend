package com.haodaone.monitoring.service;

import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.monitoring.dto.AgentTokenOtpDTO;
import com.haodaone.monitoring.dto.MonitoredDeviceDTO;
import com.haodaone.monitoring.entity.MonitoredDevice;
import com.haodaone.monitoring.entity.OtpVerification;
import com.haodaone.monitoring.entity.TokenRotationHistory;
import com.haodaone.monitoring.repository.MonitoredDeviceRepository;
import com.haodaone.monitoring.repository.OtpVerificationRepository;
import com.haodaone.monitoring.repository.TokenRotationHistoryRepository;
import com.haodaone.recruitment.service.EmailService;
import com.haodaone.user.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

@Service
public class OtpService {
    private final MonitoredDeviceRepository deviceRepository;
    private final OtpVerificationRepository otpRepository;
    private final TokenRotationHistoryRepository historyRepository;
    private final EmailService emailService;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.agent-token.otp-expiry-minutes:10}") private int expiryMinutes;
    @Value("${app.agent-token.otp-resend-seconds:60}") private int resendSeconds;
    @Value("${app.agent-token.otp-max-resends:3}") private int maxResends;

    public OtpService(MonitoredDeviceRepository deviceRepository, OtpVerificationRepository otpRepository,
                      TokenRotationHistoryRepository historyRepository, EmailService emailService) {
        this.deviceRepository = deviceRepository;
        this.otpRepository = otpRepository;
        this.historyRepository = historyRepository;
        this.emailService = emailService;
    }

    @Transactional
    public AgentTokenOtpDTO.RequestResponse requestOtp(Long deviceId, User user) {
        MonitoredDevice device = findDevice(deviceId, user);
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BadRequestException("Your account has no email address for OTP delivery");
        }
        LocalDateTime now = utcNow();
        OtpVerification verification = otpRepository
                .findTopByDevice_IdAndRequestedBy_IdAndUsedFalseAndDeletedFalseOrderByCreatedAtDesc(deviceId, user.getId())
                .orElseGet(OtpVerification::new);
        if (verification.getId() != null && verification.getLastResendAt() != null
                && verification.getLastResendAt().plusSeconds(resendSeconds).isAfter(now)) {
            throw new BadRequestException("Please wait before requesting another OTP");
        }
        if (verification.getId() != null && verification.getResendCount() >= maxResends) {
            throw new BadRequestException("OTP resend limit reached; start a new request later");
        }
        String otp = String.format("%06d", random.nextInt(1_000_000));
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        verification.setCompany(device.getCompany());
        verification.setDevice(device);
        verification.setRequestedBy(user);
        verification.setSalt(Base64.getEncoder().encodeToString(salt));
        verification.setOtpHash(hash(verification.getSalt(), otp));
        verification.setAttempts(0);
        verification.setExpiresAt(now.plusMinutes(expiryMinutes));
        verification.setLastResendAt(now);
        if (verification.getId() != null) verification.setResendCount(verification.getResendCount() + 1);
        otpRepository.save(verification);
        emailService.sendAgentTokenOtpEmail(user.getEmail(), user.getFullName(), otp, expiryMinutes, device.getDeviceName());
        return new AgentTokenOtpDTO.RequestResponse("OTP sent to your registered email address", verification.getExpiresAt());
    }

    @Transactional
    public AgentTokenOtpDTO.ConfirmResponse confirm(Long deviceId, AgentTokenOtpDTO.ConfirmRequest request,
                                                     User user, String ipAddress) {
        MonitoredDevice device = findDevice(deviceId, user);
        OtpVerification verification = otpRepository
                .findTopByDevice_IdAndRequestedBy_IdAndUsedFalseAndDeletedFalseOrderByCreatedAtDesc(deviceId, user.getId())
                .orElseThrow(() -> new BadRequestException("No active OTP request found"));
        LocalDateTime now = utcNow();
        if (verification.getExpiresAt().isBefore(now)) throw new BadRequestException("OTP has expired");
        if (verification.getAttempts() >= verification.getMaxAttempts()) throw new BadRequestException("OTP attempt limit reached");
        verification.setAttempts(verification.getAttempts() + 1);
        if (!MessageDigest.isEqual(hash(verification.getSalt(), request.otp()).getBytes(StandardCharsets.UTF_8), verification.getOtpHash().getBytes(StandardCharsets.UTF_8))) {
            otpRepository.save(verification);
            throw new BadRequestException("Invalid OTP");
        }
        verification.setUsed(true);
        verification.setUsedAt(now);
        otpRepository.save(verification);
        String previousHash = device.getAgentTokenHash();
        String rawToken = generateToken();
        String newHash = hashToken(rawToken);
        device.setAgentTokenHash(newHash);
        deviceRepository.save(device);
        TokenRotationHistory history = new TokenRotationHistory();
        history.setCompany(device.getCompany());
        history.setDevice(device);
        history.setRotatedBy(user);
        history.setPreviousAgentTokenHash(previousHash);
        history.setNewAgentTokenHash(newHash);
        history.setRotationReason(request.reason() == null || request.reason().isBlank() ? "OTP verified rotation" : request.reason().trim());
        history.setRotationIp(ipAddress);
        historyRepository.save(history);
        return new AgentTokenOtpDTO.ConfirmResponse(MonitoredDeviceDTO.from(device), rawToken);
    }

    @Transactional(readOnly = true)
    public java.util.List<AgentTokenOtpDTO.RotationHistoryResponse> history(Long deviceId, User user) {
        MonitoredDevice device = findDevice(deviceId, user);
        Long companyId = device.getCompany() == null ? null : device.getCompany().getId();
        if (companyId == null) return java.util.List.of();
        return historyRepository.findAllByDevice_IdAndCompany_IdOrderByCreatedAtDesc(deviceId, companyId).stream()
                .map(h -> new AgentTokenOtpDTO.RotationHistoryResponse(h.getId(), h.getRotationReason(), h.getRotationIp(), h.getCreatedAt()))
                .toList();
    }

    private MonitoredDevice findDevice(Long id, User user) {
        Long companyId = user.getCompany() == null ? null : user.getCompany().getId();
        return companyId == null ? deviceRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Monitored device not found: " + id))
                : deviceRepository.findByIdAndCompany_IdAndDeletedFalse(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Monitored device not found: " + id));
    }

    private String generateToken() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private String hash(String salt, String value) { return hashToken(salt + ":" + value); }
    private String hashToken(String value) {
        try { return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 not available", e); }
    }
    private LocalDateTime utcNow() { return LocalDateTime.now(ZoneOffset.UTC); }
}
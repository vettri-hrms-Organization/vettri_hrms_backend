package com.haodaone.remotesupport.service;

import com.haodaone.common.exception.BadRequestException;
import com.haodaone.company.entity.Company;
import com.haodaone.remotesupport.entity.*;
import com.haodaone.remotesupport.repository.RemoteSupportCredentialRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class RemoteSupportCredentialService {
    private final RemoteSupportCredentialRepository repository;
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();
    public RemoteSupportCredentialService(RemoteSupportCredentialRepository repository, @Value("${app.remote-support.credential-key:}") String configuredKey) {
        this.repository = repository;
        if (configuredKey == null || configuredKey.isBlank()) { key = null; return; }
        try { key = Base64.getDecoder().decode(configuredKey); } catch (IllegalArgumentException ex) { throw new IllegalStateException("REMOTE_SUPPORT_CREDENTIAL_KEY must be base64", ex); }
        if (key.length != 32) throw new IllegalStateException("REMOTE_SUPPORT_CREDENTIAL_KEY must decode to 32 bytes");
    }
    public String generate() { byte[] bytes = new byte[24]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    public boolean isConfigured() { return key != null; }
    public void store(Company company, Long deviceId, String secret, RemoteSupportStatus status) {
        if (key == null) throw new IllegalStateException("REMOTE_SUPPORT_CREDENTIAL_KEY must be configured before remote support provisioning");
        RemoteSupportCredential credential = repository.findByDeviceId(deviceId).orElseGet(RemoteSupportCredential::new);
        credential.setCompany(company); credential.setDeviceId(deviceId); credential.setEncryptedSecret(encrypt(secret)); credential.setStatus(status); credential.setRotatedAt(java.time.LocalDateTime.now()); repository.save(credential);
    }
    public String decryptForAgent(Long deviceId) {
        if (key == null) throw new IllegalStateException("REMOTE_SUPPORT_CREDENTIAL_KEY must be configured before remote support provisioning");
        RemoteSupportCredential credential = repository.findByDeviceId(deviceId).orElseThrow(() -> new IllegalStateException("Remote support credential not found"));
        try { String[] parts=credential.getEncryptedSecret().split("\\.",2); Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,Base64.getDecoder().decode(parts[0]))); return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])),StandardCharsets.UTF_8); }
        catch(Exception ex){throw new IllegalStateException("Could not decrypt remote support credential",ex);}
    }
    private String encrypt(String value) {
        try { byte[] iv = new byte[12]; random.nextBytes(iv); Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv)); byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)); return Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(encrypted); }
        catch (Exception ex) { throw new BadRequestException("Could not protect remote support credential"); }
    }
}
package cn.study.service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Write-only model credentials, encrypted at rest and bound to their connection ID. */
@Component
public class ModelCredentials {
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();
    public ModelCredentials(@Value("${MODEL_CREDENTIALS_KEY:}") String configured,
                            @Value("${auth.jwt-secret:}") String fallback) {
        String secret = configured.isBlank() ? fallback : configured;
        if (secret.length() < 32) throw new IllegalStateException("Model credential encryption key must have at least 32 characters");
        try {
            key = new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(
                ("study-model-credentials-v1\0" + secret).getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (GeneralSecurityException e) { throw new IllegalStateException("Model credential encryption unavailable"); }
    }
    public String encrypt(String value, UUID id) {
        try {
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce, id);
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] result = Arrays.copyOf(nonce, nonce.length + encrypted.length);
            System.arraycopy(encrypted, 0, result, nonce.length, encrypted.length);
            return "v1:" + Base64.getEncoder().encodeToString(result);
        } catch (GeneralSecurityException e) { throw new AiClient.Failure("AI_CREDENTIALS_UNAVAILABLE"); }
    }
    public String decrypt(String value, UUID id) {
        try {
            if (!value.startsWith("v1:")) throw new IllegalArgumentException();
            byte[] bytes = Base64.getDecoder().decode(value.substring(3));
            if (bytes.length < 29) throw new IllegalArgumentException();
            return new String(cipher(Cipher.DECRYPT_MODE, Arrays.copyOf(bytes, 12), id)
                .doFinal(Arrays.copyOfRange(bytes, 12, bytes.length)), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) { throw new AiClient.Failure("AI_CREDENTIALS_UNAVAILABLE"); }
    }
    private Cipher cipher(int mode, byte[] nonce, UUID id) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(128, nonce));
        cipher.updateAAD(("study-model:" + id).getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
}

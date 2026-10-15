package com.nl2sql.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 加解密工具（用于数据源密码）
 */
@Slf4j
@Component
public class AESUtil {

    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;
    private static final int TAG_LEN = 128;

    @Value("${nl2sql.aes.key}")
    private String key;

    private SecretKeySpec keySpec;
    private final SecureRandom random = new SecureRandom();

    @PostConstruct
    void init() {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        // 截断/填充到 32 字节
        byte[] k = new byte[32];
        System.arraycopy(keyBytes, 0, k, 0, Math.min(keyBytes.length, 32));
        this.keySpec = new SecretKeySpec(k, "AES");
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance(ALGO);
            c.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LEN, iv));
            byte[] cipher = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[IV_LEN + cipher.length];
            System.arraycopy(iv, 0, out, 0, IV_LEN);
            System.arraycopy(cipher, 0, out, IV_LEN, cipher.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("AES 加密失败", e);
        }
    }

    public String decrypt(String base64) {
        try {
            byte[] all = Base64.getDecoder().decode(base64);
            byte[] iv = new byte[IV_LEN];
            byte[] cipher = new byte[all.length - IV_LEN];
            System.arraycopy(all, 0, iv, 0, IV_LEN);
            System.arraycopy(all, IV_LEN, cipher, 0, cipher.length);
            Cipher c = Cipher.getInstance(ALGO);
            c.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LEN, iv));
            return new String(c.doFinal(cipher), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES 解密失败", e);
        }
    }
}

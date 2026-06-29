package com.upi.offline.encryption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Component
public class AESEncryption {

    private static final Logger log = LoggerFactory.getLogger(AESEncryption.class);
    private final SecretKey secretKey;

    public AESEncryption(@Value("${offlineupi.app.aesSecret}") String aesSecret) {
        log.info("Initializing AES Encryption Key from properties");
        try {
            byte[] keyBytes = aesSecret.getBytes();
            if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
                log.error("AES key length is {} bytes. It must be 16, 24, or 32 bytes.", keyBytes.length);
                throw new IllegalArgumentException("AES key must be 16, 24, or 32 bytes long");
            }
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
            log.info("AES {} bit secret key initialized successfully", keyBytes.length * 8);
        } catch (Exception e) {
            log.error("Failed to initialize AES key: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public String encrypt(String data) {
        log.info("Encryption started");
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(data.getBytes());
            String result = Base64.getEncoder().encodeToString(encrypted);
            log.info("Encryption completed successfully");
            return result;
        } catch (Exception e) {
            log.error("Encryption failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public String decrypt(String encryptedData) {
        log.info("Decryption started");
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decoded = Base64.getDecoder().decode(encryptedData);
            String result = new String(cipher.doFinal(decoded));
            log.info("Decryption completed successfully");
            return result;
        } catch (Exception e) {
            log.error("Decryption failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
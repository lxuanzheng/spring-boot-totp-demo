package com.example.totp.service;

import com.example.totp.util.Base32;
import com.example.totp.util.TotpUtil;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

/**
 * TOTP 服务层
 * 负责生成 secret、生成 otpauth URI、验证验证码
 */
@Service
public class TotpService {

    /**
     * 为用户生成 TOTP secret（Base32 编码）
     */
    public String generateSecret(String username) {
        byte[] key = new byte[20];
        new SecureRandom().nextBytes(key);
        return Base32.encode(key);
    }

    /**
     * 生成 otpauth:// 格式的 URI，用于生成二维码
     */
    public String generateOtpAuthUri(String username, String secret) {
        return TotpUtil.generateOtpAuthUri("MyApp", username, secret);
    }

    /**
     * 验证验证码
     */
    public boolean verifyCode(String secret, String code, long time) {
        return TotpUtil.verify(secret, code, time);
    }
}
package com.example.totp.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * TOTP (Time-based One-Time Password) 工具类
 * 基于 RFC 6238 实现，对应 Google Authenticator / Microsoft Authenticator
 */
public class TotpUtil {

    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final int TIME_STEP = 30;     // 30 秒一个周期
    private static final int CODE_DIGITS = 6;    // 6 位验证码

    /**
     * 生成当前时间点的 6 位 TOTP 验证码
     *
     * @param secretBase32 Base32 编码的 secret key
     * @param time         unix 时间戳（毫秒）
     * @return 6 位数字字符串
     */
    public static String generateCode(String secretBase32, long time) {
        byte[] key = Base32.decode(secretBase32);
        long counter = time / (TIME_STEP * 1000L);

        byte[] counterBytes = new byte[8];
        for (int i = 7; i >= 0; i--) {
            counterBytes[i] = (byte) (counter & 0xff);
            counter >>>= 8;
        }

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(counterBytes);

            int offset = hash[hash.length - 1] & 0xf;
            long code = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            code %= (long) Math.pow(10, CODE_DIGITS);

            return String.format("%0" + CODE_DIGITS + "d", code);
        } catch (Exception e) {
            throw new RuntimeException("TOTP generation failed", e);
        }
    }

    /**
     * 验证验证码是否匹配
     */
    public static boolean verify(String secretBase32, String code, long time) {
        String generated = generateCode(secretBase32, time);
        return generated.equals(code.trim());
    }

    /**
     * 生成 otpauth:// 格式的 URI，用于生成二维码
     */
    public static String generateOtpAuthUri(String issuer, String account, String secretBase32) {
        return String.format(
                "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=%d",
                issuer, account, secretBase32, issuer, CODE_DIGITS, TIME_STEP
        );
    }
}
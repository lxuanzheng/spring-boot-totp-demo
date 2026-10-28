package com.example.totp.service;

import com.example.totp.model.TwoFactorConfig;
import com.example.totp.util.Base32;
import com.example.totp.util.TotpUtil;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * TOTP / HOTP 服务。
 * 讨巧点：HOTP(counter=N) 与 TOTP(时间=N×30s) 走同一条 HMAC 计算，
 * 因此直接复用 TotpUtil，不新增密码学代码。
 */
@Service
public class OTPService {

    /** HOTP 验证的 look-ahead 窗口宽度 */
    private static final int HOTP_WINDOW = 10;

    private final SecureRandom random = new SecureRandom();

    public String generateSecret() {
        byte[] key = new byte[20];
        random.nextBytes(key);
        return Base32.encode(key);
    }

    public String totpCode(String secret, long timeMs) {
        return TotpUtil.generateCode(secret, timeMs);
    }

    /** HOTP 第 counter 次（counter 从 1 开始）的验证码 */
    public String hotpCode(String secret, long counter) {
        return TotpUtil.generateCode(secret, counter * 30_000L);
    }

    /** TOTP 验证：当前窗口 ±1 容错 */
    public boolean verifyTOTP(String secret, String code, long timeMs) {
        for (long t : new long[]{timeMs, timeMs - 30_000L, timeMs + 30_000L}) {
            if (totpCode(secret, t).equals(code.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * HOTP 验证：在 (counter, counter+WINDOW] 内逐个尝试；
     * 命中后 counter 对齐并持久化，跳过的码永久作废。
     */
    public boolean verifyHOTP(SecretStore store, String username, TwoFactorConfig cfg, String code) {
        for (long c = cfg.getCounter() + 1; c <= cfg.getCounter() + HOTP_WINDOW; c++) {
            if (hotpCode(cfg.getSecret(), c).equals(code.trim())) {
                cfg.setCounter(c);
                store.save(username, cfg);
                return true;
            }
        }
        return false;
    }

    /** 生成标准 otpauth:// URI（供二维码与复制） */
    public String buildOtpAuthUri(String username, TwoFactorConfig cfg) {
        String label = URLEncoder.encode("Demo:" + username, StandardCharsets.UTF_8);
        StringBuilder uri = new StringBuilder("otpauth://")
                .append(cfg.getType().name().toLowerCase())   // totp / hotp
                .append("/").append(label)
                .append("?secret=").append(cfg.getSecret())
                .append("&issuer=Demo&algorithm=SHA1&digits=6");
        if (cfg.getType() == TwoFactorConfig.Type.TOTP) {
            uri.append("&period=").append(cfg.getPeriod());
        } else {
            uri.append("&counter=0");
        }
        return uri.toString();
    }
}
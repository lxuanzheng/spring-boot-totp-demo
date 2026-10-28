package com.example.totp.controller;

import com.example.totp.model.TwoFactorConfig;
import com.example.totp.service.OTPService;
import com.example.totp.service.SecretStore;
import com.example.totp.util.QRCodeGenerator;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 2FA 设置接口（需登录）：
 * setup → 生成 secret 存 session 待验；enable → 验首个码后持久化；
 * qr.png → 生成二维码；status / disable。
 */
@RestController
@RequestMapping("/api/2fa")
public class TwoFactorController {

    private static final String SESSION_PENDING = "PENDING_2FA";

    private final SecretStore secretStore;
    private final OTPService otpService;

    public TwoFactorController(SecretStore secretStore, OTPService otpService) {
        this.secretStore = secretStore;
        this.otpService = otpService;
    }

    @GetMapping("/status")
    public Result<?> status(HttpSession session) {
        String username = AuthController.currentUser(session);
        if (username == null) {
            return Result.fail(HttpStatus.UNAUTHORIZED, "未登录");
        }
        TwoFactorConfig cfg = secretStore.get(username);
        return Result.ok(cfg == null
                ? Map.of("enabled", false)
                : Map.of("enabled", true, "type", cfg.getType().name(),
                         "counter", cfg.getCounter(), "period", cfg.getPeriod()));
    }

    /** 第一步：选择类型，生成 secret（此时还没绑定，存 session） */
    @PostMapping("/setup")
    public Result<?> setup(@RequestBody Map<String, String> body, HttpSession session) {
        String username = AuthController.currentUser(session);
        if (username == null) {
            return Result.fail(HttpStatus.UNAUTHORIZED, "未登录");
        }
        if (secretStore.get(username) != null) {
            return Result.fail("已启用 2FA，请先禁用再重新设置");
        }
        TwoFactorConfig.Type type;
        try {
            type = TwoFactorConfig.Type.valueOf(body.getOrDefault("type", "TOTP").toUpperCase());
        } catch (IllegalArgumentException e) {
            return Result.fail("类型只能是 TOTP 或 HOTP");
        }
        TwoFactorConfig pending = new TwoFactorConfig();
        pending.setType(type);
        pending.setSecret(otpService.generateSecret());
        pending.setCounter(0);
        session.setAttribute(SESSION_PENDING, pending);

        String uri = otpService.buildOtpAuthUri(username, pending);
        return Result.ok(Map.of(
                "type", type.name(),
                "secret", pending.getSecret(),
                "otpauthUri", uri));
    }

    /** 二维码：直接吃 URI，无需登录态（内容不含 secret 之外的可敏感信息，且二维码本就给人扫的） */
    @GetMapping(value = "/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qr(@RequestParam String uri) {
        return ResponseEntity.ok(QRCodeGenerator.generate(uri, 300, 300));
    }

    /** 第二步：输入手机 App 显示的码，验证通过即完成绑定 */
    @PostMapping("/enable")
    public Result<?> enable(@RequestBody Map<String, String> body, HttpSession session) {
        String username = AuthController.currentUser(session);
        if (username == null) {
            return Result.fail(HttpStatus.UNAUTHORIZED, "未登录");
        }
        Object pendingObj = session.getAttribute(SESSION_PENDING);
        if (!(pendingObj instanceof TwoFactorConfig pending)) {
            return Result.fail("请先生成二维码（POST /api/2fa/setup）");
        }
        String code = body.get("code");
        boolean ok = switch (pending.getType()) {
            case TOTP -> otpService.verifyTOTP(pending.getSecret(), code, System.currentTimeMillis());
            case HOTP -> otpService.hotpCode(pending.getSecret(), 1).equals(code.trim());  // 首码 = counter 1
        };
        if (!ok) {
            return Result.fail("验证码不正确");
        }
        pending.setCounter(pending.getType() == TwoFactorConfig.Type.HOTP ? 1 : 0);
        secretStore.save(username, pending);
        session.removeAttribute(SESSION_PENDING);
        return Result.ok("2FA 已启用");
    }

    @PostMapping("/disable")
    public Result<?> disable(HttpSession session) {
        String username = AuthController.currentUser(session);
        if (username == null) {
            return Result.fail(HttpStatus.UNAUTHORIZED, "未登录");
        }
        secretStore.delete(username);
        return Result.ok("2FA 已禁用");
    }
}
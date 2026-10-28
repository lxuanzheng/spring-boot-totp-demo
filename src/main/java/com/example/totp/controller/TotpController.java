package com.example.totp.controller;

import com.example.totp.service.TotpService;
import com.example.totp.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/totp")
public class TotpController {

    @Autowired
    private TotpService totpService;

    @Autowired
    private UserService userService;

    /**
     * 用户登录：如果没有 secret，先生成并保存
     */
    @PostMapping("/login")
    public Result<?> login(@RequestParam String username, @RequestParam String password) {
        if (!userService.authenticate(username, password)) {
            return Result.fail(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }

        String secret = userService.getSecret(username);
        if (secret == null) {
            secret = totpService.generateSecret(username);
            userService.saveSecret(username, secret);
        }

        return Result.ok("登录成功", Map.of("username", username, "secret", secret));
    }

    /**
     * 生成 otpauth:// URI，用于二维码生成
     */
    @GetMapping("/otpauth/{username}")
    public Result<?> getOtpauthUri(@PathVariable String username) {
        String secret = userService.getSecret(username);
        if (secret == null) {
            return Result.fail("该用户未设置 TOTP，请先登录生成 secret");
        }

        String uri = totpService.generateOtpAuthUri(username, secret);
        return Result.ok("二维码 URI", uri);
    }

    /**
     * 验证 6 位验证码
     */
    @PostMapping("/verify")
    public Result<?> verify(@RequestParam String username, @RequestParam String code) {
        String secret = userService.getSecret(username);
        if (secret == null) {
            return Result.fail("该用户未配置 TOTP，请先登录");
        }

        long time = Instant.now().toEpochMilli();
        boolean ok = totpService.verifyCode(secret, code, time);

        if (ok) {
            return Result.ok("验证码正确");
        }

        // 允许 ±1 个周期的容错（比如时间不同步）
        long lower = Math.max(0, time - TIME_STEP * 1000 - 1);
        long upper = time + TIME_STEP * 1000 + 1;
        for (long t = lower; t <= upper; t += TIME_STEP * 1000) {
            if (totpService.verifyCode(secret, code, t)) {
                return Result.ok("验证码正确（容错）");
            }
        }

        return Result.fail("验证码不正确");
    }

    /**
     * 重置用户 secret
     */
    @PostMapping("/reset/{username}")
    public Result<?> reset(@PathVariable String username) {
        String secret = totpService.generateSecret(username);
        userService.saveSecret(username, secret);
        return Result.ok("已重置", Map.of("secret", secret));
    }

    // 简单的分页常量
    private static final int TIME_STEP = 30; // 30秒
}
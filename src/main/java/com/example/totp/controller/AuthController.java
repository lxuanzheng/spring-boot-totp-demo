package com.example.totp.controller;

import com.example.totp.config.DemoUserProperties;
import com.example.totp.model.TwoFactorConfig;
import com.example.totp.model.UserAccount;
import com.example.totp.service.OTPService;
import com.example.totp.service.SecretStore;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final DemoUserProperties users;
    private final SecretStore secretStore;
    private final OTPService otpService;

    /** 2FA 待验会话：challengeId → (username, 过期时间)，5 分钟有效 */
    private final Map<String, Pending2FA> challenges = new ConcurrentHashMap<>();
    private static final long CHALLENGE_TTL_MS = 5 * 60_000L;

    private record Pending2FA(String username, long expireAt) {}

    public AuthController(DemoUserProperties users, SecretStore secretStore, OTPService otpService) {
        this.users = users;
        this.secretStore = secretStore;
        this.otpService = otpService;
    }

    /** 第一步：账号密码 */
    @PostMapping("/login")
    public Result<?> login(@RequestBody Map<String, String> body, HttpSession session) {
        String username = body.get("username");
        String password = body.get("password");

        UserAccount user = findUser(username);
        if (user == null || !user.getPassword().equals(password)) {
            return Result.fail(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }

        TwoFactorConfig cfg = secretStore.get(username);
        if (cfg == null) {
            // 未启用 2FA：直接登录
            session.setAttribute("user", username);
            return Result.ok("登录成功", Map.of("requires2fa", false));
        }

        String challengeId = UUID.randomUUID().toString();
        challenges.put(challengeId, new Pending2FA(username, System.currentTimeMillis() + CHALLENGE_TTL_MS));
        return Result.ok("需要二次验证", Map.of(
                "requires2fa", true,
                "challengeId", challengeId,
                "type", cfg.getType().name()));
    }

    /** 第二步：2FA 验证码 */
    @PostMapping("/login/2fa")
    public Result<?> login2fa(@RequestBody Map<String, String> body, HttpSession session) {
        Pending2FA pending = challenges.get(body.get("challengeId"));
        if (pending == null || pending.expireAt() < System.currentTimeMillis()) {
            return Result.fail(HttpStatus.UNAUTHORIZED, "验证会话已过期，请重新登录");
        }
        TwoFactorConfig cfg = secretStore.get(pending.username());
        boolean ok = switch (cfg.getType()) {
            case TOTP -> otpService.verifyTOTP(cfg.getSecret(), body.get("code"), System.currentTimeMillis());
            case HOTP -> otpService.verifyHOTP(secretStore, pending.username(), cfg, body.get("code"));
        };
        if (!ok) {
            return Result.fail(HttpStatus.UNAUTHORIZED, "验证码不正确");
        }
        challenges.remove(body.get("challengeId"));
        session.setAttribute("user", pending.username());
        return Result.ok("登录成功");
    }

    @PostMapping("/logout")
    public Result<?> logout(HttpSession session) {
        session.invalidate();
        return Result.ok("已退出");
    }

    /** 当前登录用户信息 + 2FA 状态 */
    @GetMapping("/me")
    public Result<?> me(HttpSession session) {
        String username = currentUser(session);
        if (username == null) {
            return Result.fail(HttpStatus.UNAUTHORIZED, "未登录");
        }
        UserAccount user = findUser(username);
        TwoFactorConfig cfg = secretStore.get(username);
        Map<String, Object> twoFactor = cfg == null
                ? Map.of("enabled", false)
                : Map.of("enabled", true,
                         "type", cfg.getType().name(),
                         "counter", cfg.getCounter(),
                         "period", cfg.getPeriod());
        return Result.ok(Map.of(
                "username", user.getUsername(),
                "displayName", user.getDisplayName() == null ? user.getUsername() : user.getDisplayName(),
                "twoFactor", twoFactor));
    }

    static String currentUser(HttpSession session) {
        Object user = session.getAttribute("user");
        return user instanceof String s ? s : null;
    }

    private UserAccount findUser(String username) {
        if (username == null) {
            return null;
        }
        List<UserAccount> all = users.getDemoUsers();
        for (UserAccount u : all) {
            if (u.getUsername().equals(username)) {
                return u;
            }
        }
        return null;
    }
}
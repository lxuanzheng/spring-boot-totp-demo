# Spring Boot 2FA 双因素认证 Demo

TOTP（RFC 6238）+ HOTP（RFC 4226）双协议 2FA 演示，带完整 Web 前端（登录/二次验证/个人页设置）。
算法与 Google/Microsoft Authenticator、Apple Passwords 兼容。

## 快速开始

```bash
mvn spring-boot:run
# 浏览器打开 http://localhost:8081
```

> 端口说明：`application.yml` 配置为 8081——本机 8080 被本地 nginx 常驻占用。

## 演示账号（application.yml 配置）

| 用户名 | 密码 | 显示名 |
|--------|------|--------|
| testuser | password123 | 测试用户 |
| admin | admin123 | 管理员 |

在 `app.demo-users` 下增删用户即可。2FA 密钥单独持久化在 `./data/2fa-secrets.json`（重启不丢）。

## 功能

- 登录 / 退出；未启用 2FA 直接进，已启用则进入"密码 + 6 位码"两步验证
- 个人页显示当前用户信息（显示名 + 用户名）
- 2FA 设置：**TOTP / HOTP 二选一** → 生成二维码（手机验证器 App 扫码）+ **otpauth URI 复制按钮** → 输入 App 显示的码完成绑定
- 已启用后可查看类型与状态、禁用
- 切换类型时二维码/URI 自动按新类型重新生成

## API 一览

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 账号密码登录；已启用 2FA 时返回 challengeId |
| POST | `/api/auth/login/2fa` | 第二步：验证码 + challengeId 完成登录 |
| POST | `/api/auth/logout` | 退出 |
| GET | `/api/auth/me` | 当前用户信息 + 2FA 状态 |
| GET | `/api/2fa/status` | 2FA 状态 |
| POST | `/api/2fa/setup` | body: `{type: TOTP\|HOTP}`，返回 secret + otpauthUri（存 session 待验） |
| GET | `/api/2fa/qr.png?uri=` | 按 URI 生成二维码 PNG |
| POST | `/api/2fa/enable` | body: `{code}`，验首码后正式绑定 |
| POST | `/api/2fa/disable` | 禁用 2FA |

## 实现要点

- 用户来自 `application.yml`（`@ConfigurationProperties`），不在代码里写死
- HOTP(counter=N) ≡ TOTP(时间=N×30s)，`OTPService` 复用 `TotpUtil` 一套 HMAC 计算
- HOTP 验证用 look-ahead 窗口（10），命中后 counter 对齐并持久化；跳过的码永久作废
- 2FA 登录挑战为 5 分钟有效的内存 challenge（演示够用；生产应换缓存 + 限流）
- 前端原生 HTML/JS（`static/`），无构建步骤；验证码输入框带 `autocomplete="one-time-code"`

## 工程结构

```
Sources 同 v1.0.0（Base32 / SHA1... 见下）
src/main/java/com/example/totp/
  config/DemoUserProperties.java   YAML 用户配置注入
  model/UserAccount.java           演示用户
  model/TwoFactorConfig.java       2FA 配置(type/secret/counter)
  service/OTPService.java          TOTP/HOTP 计算 + otpauth URI
  service/SecretStore.java         2FA 密钥 JSON 文件存储
  controller/AuthController.java   登录/二次验证/退出/me
  controller/TwoFactorController.java  setup/qr/enable/disable/status
  util/{Base32, SHA1→内置, TotpUtil, QRCodeGenerator}
src/main/resources/static/         index.html / app.js / style.css
```

## 版本记录

- **v1.1.0**：Web 前端（登录/退出/个人页）、TOTP+HOTP 双协议设置流（二维码 + URI 复制）、
  用户改 YAML 配置（2 个默认用户）、密钥持久化 data/2fa-secrets.json、端口改 8081
- **v1.0.0**：纯 API 版 TOTP Demo

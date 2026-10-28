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

## 为什么 `hotpCode` 里是 `counter * 30_000L`？

读 `OTPService.hotpCode` 时最容易卡住的一行：

```java
public String hotpCode(String secret, long counter) {
    return TotpUtil.generateCode(secret, counter * 30_000L);
}
```

**目的：让 `TotpUtil` 内部算出的 counter 恰好等于 HOTP 的第 N 次。这是在复用同一个算法，不是在做时间换算。**

`TotpUtil.generateCode(secret, time)` 的第一步永远是：

```java
long counter = time / (TIME_STEP * 1000L);   // 即 time / 30_000
```

它不管传入的是真实时间还是别的什么数，一律除以 30,000 当作"第几格"。所以要得到"第 N 次"的码，只要传一个能让除法结果等于 N 的数，即 `N × 30_000`：

| 想算的 | 需要内部 counter = | 传入的 time |
|---|---|---|
| HOTP 第 1 次 | 1 | 30_000 |
| HOTP 第 52 次 | 52 | 1,560,000 |
| HOTP 第 N 次 | N | N × 30_000 |

**为什么合法**——这是 RFC 6238 的定义，不是投机取巧：

```
HOTP:  HMAC(secret, counter)        counter = 用过的次数（存出来的状态）
TOTP:  HMAC(secret, ⌊time ÷ 30⌋)    counter = 第几个 30 秒（从时间算出来的）
              ↑ 同一段算法，唯一区别是 counter 的来源
```

"HOTP 第 N 次"与"落在第 N 格的时间片"生成的是**同一个码**。证据就在本项目的冒烟测试里：

```
HOTP counter=1 → 287082
TOTP 6位 t=59  → 287082     （59 ÷ 30 = 1，同格同码）
```

细节：`30_000` = 30 秒 × 1000（`TotpUtil` 吃毫秒，对应 `System.currentTimeMillis()` 的惯例）；`L` 后缀是 long 字面量，意图明确、避免窄化计算的歧义。

验证端同理：`verifyHOTP` 在窗口 `(counter, counter+10]` 内逐个候选 counter 调 `hotpCode`——每次就是一次独立的 HMAC 计算，等价于 RFC 4226 标准验证流程，没有为此多写一行 SHA1/HMAC 代码。

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

# Spring Boot TOTP 双因素认证 Demo

## 项目说明

一个使用 TOTP（Time-based One-Time Password）实现的 2FA（双因素认证）Demo，基于 Google Authenticator / Microsoft Authenticator 兼容算法。

## 快速开始

### 1. 安装依赖

```bash
cd /Users/lixiuyuan/Codes/spring-boot-totp-demo
mvn clean install
```

### 2. 启动应用

```bash
mvn spring-boot:run
```

### 3. 使用 API 测试

#### 步骤一：登录获取 secret

```bash
curl -X POST 'http://localhost:8080/api/totp/login?username=testuser&password=password123'
```

返回示例：
```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "username": "testuser",
    "secret": "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
  }
}
```

#### 步骤二：获取 otpauth URI（二维码链接）

```bash
curl http://localhost:8080/api/totp/otpauth/testuser
```

返回示例：
```json
{
  "code": 200,
  "message": "二维码 URI",
  "data": "otpauth://totp/MyApp:testuser?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=MyApp&algorithm=SHA1&digits=6&period=30"
}
```

将上述 URI 作为二维码内容，用 Google Authenticator 或 Apple Passwords 扫码即可添加。

#### 步骤三：生成并验证验证码

```bash
curl -X POST 'http://localhost:8080/api/totp/verify?username=testuser&code=<6位验证码>'
```

返回示例：
```json
{
  "code": 200,
  "message": "验证码正确"
}
```

#### 步骤四：获取二维码图片

```bash
curl -o qr.png http://localhost:8080/api/qr/testuser
```

返回 Base64 PNG 图片，可直接嵌入 HTML。

### 4. 完整流程伪代码

```
前端打开登录页
  ↓
输入用户名密码 → POST /api/totp/login
  ↓
服务端返回 { secret: "GEZ..." }
  ↓
前端调用 GET /api/qr/{username} 获取二维码
  ↓
用户手机扫码绑定 secret
  ↓
用户在验证码输入框输出生成的 6 位码 → POST /api/totp/verify
  ↓
服务端验证通过 → 返回 token（实际项目中可签 JWT）
```

## API 一览

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/totp/login` | 用户登录（同时生成或检查 TOTP secret） |
| GET  | `/api/totp/otpauth/{username}` | 获取 otpauth URI 用于二维码 |
| GET  | `/api/qr/{username}` | 获取 Base64 编码的二维码图片 |
| POST | `/api/totp/verify` | 验证验证码（支持容错 ±1 个周期） |
| POST | `/api/totp/reset/{username}` | 重置用户 TOTP secret |

## 技术要点

- **TOTP 实现**：RFC 6238 标准，HMAC-SHA1 + 30 秒时间分片
- **Base32 编码**：使用 Apache Commons Codec 解码
- **防漂移**：验证时允许 ±1 个周期的时间差
- **二维码**：使用 ZXing 库生成 PNG

## 集成到你的项目

如果项目需要实际集成到 OMS 等业务系统，主要改动点：
1. 将内存存储替换为数据库（如 spddb 的 t_sys_user / t_security_token 表）
2. 在 Controller 中对接业务逻辑：登录成功后签 JWT Token
3. 删除 Demo 的 `UserService` 和 `Result` 类，替换为业务服务

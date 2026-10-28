package com.example.totp.model;

/** 某用户已启用的 2FA 配置（持久化到 data/2fa-secrets.json） */
public class TwoFactorConfig {

    public enum Type { TOTP, HOTP }

    private Type type;
    private String secret;
    /** HOTP：已成功验证的最大 counter（下次窗口从 counter+1 开始） */
    private long counter;
    /** TOTP：时间周期（秒），当前实现固定 30 */
    private int period = 30;

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public long getCounter() { return counter; }
    public void setCounter(long counter) { this.counter = counter; }
    public int getPeriod() { return period; }
    public void setPeriod(int period) { this.period = period; }
}
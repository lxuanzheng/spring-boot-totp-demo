package com.example.totp.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 简单的用户存储（演示用）
 * 实际项目中请替换为数据库存储
 */
@Service
public class UserService {

    // 内存存储用户信息：username -> secret
    private final Map<String, String> userSecrets = new ConcurrentHashMap<>();

    // 内存存储用户信息：username -> TOTP secret
    private final Map<String, User> users = new ConcurrentHashMap<>();

    public UserService() {
        // 示例用户
        users.put("testuser", new User("testuser", "password123"));
    }

    public boolean authenticate(String username, String password) {
        User user = users.get(username);
        return user != null && user.getPassword().equals(password);
    }

    public String getSecret(String username) {
        return userSecrets.get(username);
    }

    public void saveSecret(String username, String secret) {
        userSecrets.put(username, secret);
    }

    public boolean hasSecret(String username) {
        return userSecrets.containsKey(username);
    }

    public static class User {
        private String username;
        private String password;

        public User() {}

        public User(String username, String password) {
            this.username = username;
            this.password = password;
        }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
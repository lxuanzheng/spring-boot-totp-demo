package com.example.totp.service;

import com.example.totp.model.TwoFactorConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 2FA 密钥的文件存储（./data/2fa-secrets.json）。
 * 替代早期版本的内存 Map——重启后已绑定的 2FA 不丢，HOTP counter 也在这里对齐。
 */
@Service
public class SecretStore {

    private final File file = new File("data/2fa-secrets.json");
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, TwoFactorConfig> secrets = new ConcurrentHashMap<>();

    public SecretStore() {
        load();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        try {
            Map<String, TwoFactorConfig> loaded = mapper.readValue(file,
                    mapper.getTypeFactory().constructMapType(Map.class, String.class, TwoFactorConfig.class));
            secrets.putAll(loaded);
        } catch (IOException e) {
            throw new RuntimeException("读取 " + file + " 失败", e);
        }
    }

    public TwoFactorConfig get(String username) {
        return secrets.get(username);
    }

    public synchronized void save(String username, TwoFactorConfig config) {
        secrets.put(username, config);
        persist();
    }

    public synchronized void delete(String username) {
        secrets.remove(username);
        persist();
    }

    private void persist() {
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, secrets);
        } catch (IOException e) {
            throw new RuntimeException("写入 " + file + " 失败", e);
        }
    }
}
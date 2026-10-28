package com.example.totp.config;

import com.example.totp.model.UserAccount;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** application.yml 中 app.demo-users 列表 */
@ConfigurationProperties(prefix = "app")
public class DemoUserProperties {

    private List<UserAccount> demoUsers = new ArrayList<>();

    public List<UserAccount> getDemoUsers() { return demoUsers; }
    public void setDemoUsers(List<UserAccount> demoUsers) { this.demoUsers = demoUsers; }
}
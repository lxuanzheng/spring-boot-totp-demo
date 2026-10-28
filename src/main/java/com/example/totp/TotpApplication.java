package com.example.totp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TotpApplication {
    public static void main(String[] args) {
        SpringApplication.run(TotpApplication.class, args);
    }
}
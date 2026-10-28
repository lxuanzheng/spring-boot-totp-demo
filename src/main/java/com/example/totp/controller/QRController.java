package com.example.totp.controller;

import com.example.totp.service.TotpService;
import com.example.totp.service.UserService;
import com.example.totp.util.QRCodeGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * 生成二维码图片的 Controller
 * 前端可用 <img src="data:image/png;base64,..." /> 直接显示
 */
@RestController
@RequestMapping("/api/qr")
public class QRController {

    @Autowired
    private TotpService totpService;

    @Autowired
    private UserService userService;

    /**
     * 生成二维码图片（Base64 编码）
     * 用法：GET /api/qr/{username}
     * 返回 Base64 编码的 PNG 图片
     */
    @GetMapping(value = "/{username}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getQRCode(@PathVariable String username) {
        String secret = userService.getSecret(username);
        if (secret == null) {
            return ResponseEntity.notFound().build();
        }

        String uri = totpService.generateOtpAuthUri(username, secret);
        byte[] pngBytes = QRCodeGenerator.generate(uri, 300, 300);
        return ResponseEntity.ok().body(pngBytes);
    }
}
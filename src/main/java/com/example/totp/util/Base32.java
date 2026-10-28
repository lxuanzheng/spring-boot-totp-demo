package com.example.totp.util;

/**
 * Base32 编解码工具类（RFC 4648）
 * 用于 TOTP secret 的编码，避免依赖 commons-codec
 */
public class Base32 {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    /**
     * 编码为 Base32 字符串（不填充）
     */
    public static String encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int bits = 0;
        int value = 0;
        int index = 0;

        for (byte b : data) {
            value = (value << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                sb.append(ALPHABET.charAt((value >> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }

        if (bits > 0) {
            sb.append(ALPHABET.charAt((value << (5 - bits)) & 0x1F));
        }

        return sb.toString();
    }

    /**
     * 从 Base32 字符串解码为字节数组
     */
    public static byte[] decode(String base32) {
        String clean = base32.replaceAll("[\\s=]", "").toUpperCase();
        int length = clean.length();
        byte[] result = new byte[length * 5 / 8];
        int index = 0;
        int bits = 0;
        int value = 0;

        for (int i = 0; i < length; i++) {
            int charValue = ALPHABET.indexOf(clean.charAt(i));
            if (charValue < 0) continue;

            value = (value << 5) | charValue;
            bits += 5;
            if (bits >= 8) {
                result[index++] = (byte) ((value >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }

        // 截断到实际长度
        byte[] trimmed = new byte[index];
        System.arraycopy(result, 0, trimmed, 0, index);
        return trimmed;
    }
}
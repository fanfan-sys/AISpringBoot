package com.example.aispringboot;

import org.jasypt.encryption.StringEncryptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class JasyptTest {

    @Autowired
    private StringEncryptor stringEncryptor;

    @Test
    void encryptPassword() {
        // 要加密的原始密码
        String rawPassword = "root";
        
        // 加密密码
        String encryptedPassword = stringEncryptor.encrypt(rawPassword);
        
        System.out.println("原始密码: " + rawPassword);
        System.out.println("加密后的密码: " + encryptedPassword);
    }
}
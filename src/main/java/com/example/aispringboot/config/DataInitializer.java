package com.example.aispringboot.config;

import com.example.aispringboot.entity.ERole;
import com.example.aispringboot.entity.Role;
import com.example.aispringboot.repository.RoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(RoleRepository roleRepository) {
        return args -> {
            // 初始化角色数据
            if (!roleRepository.findByName(ERole.ROLE_USER).isPresent()) {
                roleRepository.save(new Role(ERole.ROLE_USER));
            }
            if (!roleRepository.findByName(ERole.ROLE_ADMIN).isPresent()) {
                roleRepository.save(new Role(ERole.ROLE_ADMIN));
            }
            if (!roleRepository.findByName(ERole.ROLE_MODERATOR).isPresent()) {
                roleRepository.save(new Role(ERole.ROLE_MODERATOR));
            }
        };
    }
}
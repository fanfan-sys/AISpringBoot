package com.example.aispringboot.controller;

import java.util.HashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aispringboot.entity.ERole;
import com.example.aispringboot.entity.Role;
import com.example.aispringboot.entity.User;
import com.example.aispringboot.payload.request.LoginRequest;
import com.example.aispringboot.payload.request.SignupRequest;
import com.example.aispringboot.payload.response.JwtResponse;
import com.example.aispringboot.payload.response.MessageResponse;
import com.example.aispringboot.repository.RoleRepository;
import com.example.aispringboot.repository.UserRepository;
import com.example.aispringboot.security.jwt.JwtUtils;
import com.example.aispringboot.security.services.UserDetailsImpl;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JwtUtils jwtUtils;

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        logger.info("Login attempt for username: {}", loginRequest.getUsername());
        logger.info("Login request body: {}", loginRequest);
        
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

            logger.info("Authentication successful for username: {}", loginRequest.getUsername());
            
            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwt = jwtUtils.generateJwtToken(authentication);
            logger.info("JWT generated for username: {}", loginRequest.getUsername());

            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            logger.info("User details: {}", userDetails);
            
            List<String> roles = userDetails.getAuthorities().stream()
                    .map(item -> item.getAuthority())
                    .collect(Collectors.toList());
            logger.info("User roles: {}", roles);

            JwtResponse response = new JwtResponse(jwt, 
                                                 userDetails.getId(), 
                                                 userDetails.getUsername(), 
                                                 userDetails.getEmail(), 
                                                 roles);
            logger.info("Login response: {}", response);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Login failed for username: {}", loginRequest.getUsername());
            logger.error("Login error: {}", e.getMessage());
            logger.error("Login error stack trace:", e);
            throw e;
        }
    }

    // 测试接口
    @GetMapping("/test")
    public ResponseEntity<?> test() {
        return ResponseEntity.ok("Test endpoint works!");
    }
    
    // 查看用户密码状态的测试接口
    @GetMapping("/test-users")
    public ResponseEntity<?> testUsers() {
        try {
            // 查询所有用户
            List<User> users = userRepository.findAll();
            
            // 构建响应，只返回用户名和密码的前20个字符
            List<Map<String, Object>> response = users.stream()
                .map(user -> {
                    Map<String, Object> userInfo = new HashMap<>();
                    userInfo.put("id", user.getId());
                    userInfo.put("username", user.getUsername());
                    userInfo.put("password_length", user.getPassword().length());
                    userInfo.put("password_prefix", user.getPassword().substring(0, Math.min(20, user.getPassword().length())));
                    return userInfo;
                })
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error in testUsers: {}", e.getMessage());
            logger.error("Error stack trace:", e);
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
    
    @GetMapping("/test-get")
    public ResponseEntity<?> testGet() {
        try {
            // 查询所有用户
            List<User> users = userRepository.findAll();
            return ResponseEntity.ok("Total users: " + users.size());
        } catch (Exception e) {
            logger.error("Error in testGet: {}", e.getMessage());
            logger.error("Error stack trace:", e);
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
    
    @PostMapping("/test-save")
    public ResponseEntity<?> testSave() {
        logger.info("testSave method called");
        try {
            // 创建一个最简单的用户对象
            User user = new User();
            String timestamp = String.valueOf(System.currentTimeMillis());
            String username = "testuser" + timestamp;
            String email = "test" + timestamp + "@example.com";
            
            logger.info("Creating user with username: {} and email: {}", username, email);
            logger.debug("Creating test user with username: {}", username);
            
            user.setUsername(username);
            user.setEmail(email);
            user.setPassword(encoder.encode("password")); // 使用密码加密器
            logger.debug("Password encoded successfully");
            
            // 为用户分配默认角色 ROLE_USER
            Set<Role> roles = new HashSet<>();
            logger.debug("Attempting to find ROLE_USER role");
            Role userRole = roleRepository.findByName(ERole.ROLE_USER)
                    .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
            logger.debug("Found ROLE_USER role with id: {}", userRole.getId());
            roles.add(userRole);
            logger.debug("Added role to user");
            user.setRoles(roles);
            
            logger.info("User object created: {}", user);
            
            // 保存到数据库
            User savedUser = userRepository.save(user);
            
            logger.info("User saved successfully with ID: {}", savedUser.getId());
            logger.debug("User saved successfully with id: {}", savedUser.getId());
            logger.debug("Saved user has roles: {}", savedUser.getRoles());
            
            // 返回完整的用户信息，包括动态生成的用户名
            Map<String, Object> response = new HashMap<>();
            response.put("id", savedUser.getId());
            response.put("username", savedUser.getUsername());
            response.put("email", savedUser.getEmail());
            response.put("password", "password"); // 返回明文密码仅用于测试
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error saving user: {}", e.getMessage());
            logger.error("Error stack trace:", e);
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        logger.info("Registering user: {}", signUpRequest.getUsername());
        
        try {
            // 显式检查密码长度
            if (signUpRequest.getPassword().length() < 6) {
                logger.error("Password too short: {}", signUpRequest.getPassword());
                return ResponseEntity
                        .badRequest()
                        .body(new MessageResponse("Error: Password must be at least 6 characters long!"));
            }
            
            if (signUpRequest.getPassword().length() > 40) {
                logger.error("Password too long: {}", signUpRequest.getPassword());
                return ResponseEntity
                        .badRequest()
                        .body(new MessageResponse("Error: Password must be at most 40 characters long!"));
            }
            
            // 检查用户名是否已经存在
            if (userRepository.existsByUsername(signUpRequest.getUsername())) {
                logger.error("Username already exists: {}", signUpRequest.getUsername());
                return ResponseEntity
                        .badRequest()
                        .body(new MessageResponse("Error: Username is already taken!"));
            }

            // 检查邮箱是否已经存在
            if (userRepository.existsByEmail(signUpRequest.getEmail())) {
                logger.error("Email already exists: {}", signUpRequest.getEmail());
                return ResponseEntity
                        .badRequest()
                        .body(new MessageResponse("Error: Email is already in use!"));
            }

            // 创建用户并加密密码
            User user = new User();
            user.setUsername(signUpRequest.getUsername());
            user.setEmail(signUpRequest.getEmail());
            user.setPassword(encoder.encode(signUpRequest.getPassword()));
            
            logger.info("Created user object: {}", user.getUsername());
            
            // 为用户分配默认角色 ROLE_USER
            Set<Role> roles = new HashSet<>();
            Role userRole = roleRepository.findByName(ERole.ROLE_USER)
                    .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
            roles.add(userRole);
            user.setRoles(roles);
            
            logger.info("Saving user to database: {}", user.getUsername());
            
            User savedUser = userRepository.save(user);
            logger.info("User registered successfully: {}, ID: {}", savedUser.getUsername(), savedUser.getId());

            return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
        } catch (Exception e) {
            logger.error("Error registering user: {}", e.getMessage());
            logger.error("Error stack trace:", e);
            return ResponseEntity
                    .internalServerError()
                    .body(new MessageResponse("Error: Registration failed - " + e.getMessage()));
        }
    }
}
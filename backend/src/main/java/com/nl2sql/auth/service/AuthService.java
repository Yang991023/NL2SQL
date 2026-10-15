package com.nl2sql.auth.service;

import com.nl2sql.auth.dto.LoginRequest;
import com.nl2sql.auth.dto.LoginResponse;
import com.nl2sql.auth.entity.User;
import com.nl2sql.auth.repo.UserRepository;
import com.nl2sql.common.exception.BizException;
import com.nl2sql.security.jwt.JwtUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtUtil jwtUtil;

    /**
     * 启动时塞一个 admin/admin123 演示账号
     */
    @PostConstruct
    @Transactional
    public void seed() {
        if (userRepo.findByUsername("admin").isEmpty()) {
            User u = new User();
            u.setUsername("admin");
            u.setPassword(encoder.encode("admin123"));
            u.setNickname("管理员");
            userRepo.save(u);
        }
    }

    public LoginResponse login(LoginRequest req) {
        User u = userRepo.findByUsername(req.getUsername())
                .orElseThrow(() -> new BizException(401, "账号或密码错误"));
        if (!encoder.matches(req.getPassword(), u.getPassword())) {
            throw new BizException(401, "账号或密码错误");
        }
        if (Boolean.FALSE.equals(u.getEnabled())) {
            throw new BizException(403, "账号已禁用");
        }
        String token = jwtUtil.generate(u.getId(), u.getUsername());
        return new LoginResponse(token, u.getId(), u.getUsername(), u.getNickname());
    }
}

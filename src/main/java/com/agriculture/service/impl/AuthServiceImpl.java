package com.agriculture.service.impl;

import com.agriculture.common.BusinessException;
import com.agriculture.dto.LoginDTO;
import com.agriculture.dto.RegisterDTO;
import com.agriculture.entity.User;
import com.agriculture.mapper.UserMapper;
import com.agriculture.security.JwtUtil;
import com.agriculture.service.AuthService;
import com.agriculture.vo.LoginVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthServiceImpl(UserMapper userMapper,
                           PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Boolean register(RegisterDTO dto) {
        if (dto.getUsername() == null || dto.getUsername().isBlank()) {
            throw new BusinessException(400, "username is required");
        }
        if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw new BusinessException(400, "password is required");
        }

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, dto.getUsername());

        User existUser = userMapper.selectOne(wrapper);
        if (existUser != null) {
            throw new BusinessException(400, "username already exists");
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname());
        user.setRole("USER");

        return userMapper.insert(user) > 0;
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        if (dto.getUsername() == null || dto.getUsername().isBlank()) {
            throw new BusinessException(400, "username is required");
        }
        if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw new BusinessException(400, "password is required");
        }

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, dto.getUsername());

        User user = userMapper.selectOne(wrapper);
        if (user == null) {
            throw new BusinessException(400, "user does not exist");
        }

        boolean matched = passwordEncoder.matches(dto.getPassword(), user.getPassword());
        if (!matched) {
            throw new BusinessException(400, "password is incorrect");
        }

        String token = jwtUtil.generateToken(
                user.getId(),
                user.getUsername(),
                user.getRole()
        );

        return new LoginVO(
                token,
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getRole()
        );
    }
}

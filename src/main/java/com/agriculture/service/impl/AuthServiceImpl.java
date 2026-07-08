package com.agriculture.service.impl;

import com.agriculture.common.BusinessException;
import com.agriculture.dto.LoginDTO;
import com.agriculture.dto.RegisterDTO;
import com.agriculture.entity.User;
import com.agriculture.entity.UserFace;
import com.agriculture.mapper.UserFaceMapper;
import com.agriculture.mapper.UserMapper;
import com.agriculture.security.JwtUtil;
import com.agriculture.service.AuthService;
import com.agriculture.service.FaceModelClient;
import com.agriculture.vo.LoginVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final UserFaceMapper userFaceMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final FaceModelClient faceModelClient;

    public AuthServiceImpl(UserMapper userMapper,
                           UserFaceMapper userFaceMapper,
                           PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil,
                           FaceModelClient faceModelClient) {
        this.userMapper = userMapper;
        this.userFaceMapper = userFaceMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.faceModelClient = faceModelClient;
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

        User user = getUserByUsername(dto.getUsername());

        boolean matched = passwordEncoder.matches(dto.getPassword(), user.getPassword());
        if (!matched) {
            throw new BusinessException(400, "password is incorrect");
        }

        return buildLoginVO(user);
    }

    @Override
    public Boolean registerFace(String username, MultipartFile file) {
        if (username == null || username.isBlank()) {
            throw new BusinessException(400, "username is required");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "face image is required");
        }

        User user = getUserByUsername(username);
        String feature = faceModelClient.extractFeature(file);

        LambdaQueryWrapper<UserFace> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFace::getUserId, user.getId());

        UserFace exist = userFaceMapper.selectOne(wrapper);
        if (exist == null) {
            UserFace userFace = new UserFace();
            userFace.setUserId(user.getId());
            userFace.setUsername(user.getUsername());
            userFace.setFaceFeature(feature);
            return userFaceMapper.insert(userFace) > 0;
        }

        exist.setUsername(user.getUsername());
        exist.setFaceFeature(feature);
        return userFaceMapper.updateById(exist) > 0;
    }

    @Override
    public LoginVO faceLogin(String username, MultipartFile file) {
        if (username == null || username.isBlank()) {
            throw new BusinessException(400, "username is required");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "face image is required");
        }

        User user = getUserByUsername(username);

        LambdaQueryWrapper<UserFace> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFace::getUserId, user.getId());

        UserFace userFace = userFaceMapper.selectOne(wrapper);
        if (userFace == null) {
            throw new BusinessException(400, "user face is not registered");
        }

        boolean matched = faceModelClient.compareFace(file, userFace.getFaceFeature());
        if (!matched) {
            throw new BusinessException(400, "face verification failed");
        }

        return buildLoginVO(user);
    }

    @Override
    public Boolean hasFace(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }

        LambdaQueryWrapper<UserFace> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFace::getUsername, username);

        return userFaceMapper.selectCount(wrapper) > 0;
    }

    private User getUserByUsername(String username) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);

        User user = userMapper.selectOne(wrapper);
        if (user == null) {
            throw new BusinessException(400, "user does not exist");
        }
        return user;
    }

    private LoginVO buildLoginVO(User user) {
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

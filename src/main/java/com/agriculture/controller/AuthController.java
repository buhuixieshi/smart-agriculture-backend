package com.agriculture.controller;

import com.agriculture.aspect.OperationLogRecord;
import com.agriculture.common.Result;
import com.agriculture.dto.LoginDTO;
import com.agriculture.dto.RegisterDTO;
import com.agriculture.service.AuthService;
import com.agriculture.vo.LoginVO;
import io.jsonwebtoken.Claims;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Result<Boolean> register(@RequestBody RegisterDTO dto) {
        return Result.ok(authService.register(dto));
    }

    @PostMapping("/login")
    @OperationLogRecord(type = "USER_LOGIN", target = "user", detail = "用户登录")
    public Result<LoginVO> login(@RequestBody LoginDTO dto) {
        return Result.ok(authService.login(dto));
    }

    @PostMapping("/face/register")
    public Result<Boolean> registerFace(Authentication authentication,
                                        @RequestParam("file") MultipartFile file) {
        if (authentication == null) {
            return Result.fail(401, "unauthorized");
        }

        return Result.ok(authService.registerFace(authentication.getName(), file));
    }

    @PostMapping("/face/login-auto")
    @OperationLogRecord(type = "FACE_LOGIN_AUTO", target = "user", detail = "face login auto")
    public Result<LoginVO> faceLoginAuto(@RequestParam("file") MultipartFile file) {
        return Result.ok(authService.faceLoginAuto(file));
    }

    @GetMapping("/face/status")
    public Result<Boolean> faceStatus(@RequestParam("username") String username) {
        return Result.ok(authService.hasFace(username));
    }

    @GetMapping("/me")
    public Result<?> me(Authentication authentication) {
        if (authentication == null) {
            return Result.fail(401, "unauthorized");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("username", authentication.getName());
        data.put("authorities", authentication.getAuthorities());

        Object details = authentication.getDetails();
        if (details instanceof Claims claims) {
            data.put("userId", claims.get("userId"));
            data.put("role", claims.get("role"));
        }

        return Result.ok(data);
    }
}

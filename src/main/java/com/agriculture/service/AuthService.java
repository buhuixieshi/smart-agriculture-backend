package com.agriculture.service;

import com.agriculture.dto.LoginDTO;
import com.agriculture.dto.RegisterDTO;
import com.agriculture.vo.LoginVO;
import org.springframework.web.multipart.MultipartFile;

public interface AuthService {

    Boolean register(RegisterDTO dto);

    LoginVO login(LoginDTO dto);

    Boolean registerFace(String username, MultipartFile file);

    LoginVO faceLoginAuto(MultipartFile file);

    Boolean hasFace(String username);
}

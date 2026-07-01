package com.agriculture.service;

import com.agriculture.dto.LoginDTO;
import com.agriculture.dto.RegisterDTO;
import com.agriculture.vo.LoginVO;

public interface AuthService {

    Boolean register(RegisterDTO dto);

    LoginVO login(LoginDTO dto);
}

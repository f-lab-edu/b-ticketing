package com.bticketing.main.controller;

import com.bticketing.main.dto.UserDto;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class AuthController {
    //회원가입 api
    @PostMapping("/register")
    public UserDto register(@RequestBody UserDto userDto) {
        return userDto;
    }

    //로그인 api
    @PostMapping("/login")
    public UserDto login(@RequestBody UserDto userDto) {
        return userDto;
    }
}

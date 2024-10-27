package com.bticketing.main.controller;

import com.bticketing.main.dto.UserDto;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class AuthController {
    //회원가입 api
    @PostMapping("/register")
    public String register(@RequestBody UserDto userDto) {
        return "회원가입 성공 " + userDto.getName();
    }
    //로그인 api
    @PostMapping("/login")
    public String login(@RequestBody UserDto userDto) {
        return "로그인 성공 " + userDto.getEmail();
    }
}

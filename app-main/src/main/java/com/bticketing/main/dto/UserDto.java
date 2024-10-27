package com.bticketing.main.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class UserDto {
    private final int userId;
    private final String name;
    private final String email;
    private final String password;

}

package com.bticketing.main.dto;

import com.bticketing.main.enums.UserType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class UserDto {
    private final int userId; // 사용자 ID, 고유 식별자
    private final String name; // 사용자 이름
    private final String email;  // 사용자 이메일 (로그인 및 식별용)
    private final String password;  // 사용자 비밀번호
    private final UserType userType; // 사용자 유형 (VIP 또는 REGULAR), 선 예매 권한 확인에 사용

}

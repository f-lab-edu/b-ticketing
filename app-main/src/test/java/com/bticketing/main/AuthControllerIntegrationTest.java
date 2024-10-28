package com.bticketing.main;

import com.bticketing.main.dto.UserDto;
import com.bticketing.main.enums.UserType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AuthControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void testRegisterUser() {
        // given
        UserDto userDto = new UserDto(1, "홍길동", "chan@example.com", "1q2w3e4r", UserType.REGULAR);
        HttpEntity<UserDto> request = new HttpEntity<>(userDto);

        // when
        ResponseEntity<String> response = restTemplate.postForEntity("/register", request, String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("회원가입 성공 홍길동");
    }
}

package com.bticketing.main;

import com.bticketing.main.dto.SeatSelectionDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SeatSelectionControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void testAutoAssignSeats() {
        // given
        SeatSelectionDto requestDto = new SeatSelectionDto(1,2,3, null);
        HttpEntity<SeatSelectionDto> request = new HttpEntity<>(requestDto);

        // when
        ResponseEntity<String> response = restTemplate.postForEntity("/seats/auto-assign", request, String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("자동 좌석 선택 완료: 3개 좌석 배정");
    }

    @Test
    public void testSelectSeats() {
        // given
        SeatSelectionDto requestDto = new SeatSelectionDto(0,0,0, new int[]{101, 102, 103});
        HttpEntity<SeatSelectionDto> request = new HttpEntity<>(requestDto);

        // when
        ResponseEntity<String> response = restTemplate.postForEntity("/seats/select", request, String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("수동 좌석 선택 완료: 좌석 ID [101, 102, 103]");
    }

    @Test
    public void testCheckVIPAccess() {
        // given
        String url = "/seats/vip-access?userType=VIP";

        // when
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("선예매 유저입니다.");
    }
}

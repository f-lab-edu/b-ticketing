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
        SeatSelectionDto seatSelectionDto = new SeatSelectionDto();
        seatSelectionDto.setSeatCount(3);

        // when
        ResponseEntity<SeatSelectionDto> response = restTemplate.postForEntity("/seats/auto-assign", seatSelectionDto, SeatSelectionDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getSeatCount()).isEqualTo(3);
    }

    @Test
    public void testSelectSeats() {
        // given
        SeatSelectionDto seatSelectionDto = new SeatSelectionDto();
        seatSelectionDto.setSeatIds(new int[]{101, 102, 103});

        // when
        ResponseEntity<SeatSelectionDto> response = restTemplate.postForEntity("/seats/select", seatSelectionDto, SeatSelectionDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getSeatIds()).containsExactly(101, 102, 103);
    }

    @Test
    public void testCheckVIPAccess_VIPUser() {
        // given
        String url = "/seats/vip-access?userType=VIP";

        // when
        ResponseEntity<SeatSelectionDto> response = restTemplate.getForEntity(url, SeatSelectionDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isVipAccess()).isTrue();
    }

    @Test
    public void testCheckVIPAccess_RegularUser() {
        // given
        String url = "/seats/vip-access?userType=REGULAR";

        // when
        ResponseEntity<SeatSelectionDto> response = restTemplate.getForEntity(url, SeatSelectionDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isVipAccess()).isFalse();
    }
}

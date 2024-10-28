package com.bticketing.main;

import com.bticketing.main.dto.ScheduleDto;
import com.bticketing.main.dto.SeatSectionDto;
import com.bticketing.main.dto.SeatSelectionDto;
import com.bticketing.main.dto.UserDto;
import com.bticketing.main.enums.UserType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AllControllersIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void testAuthController_RegisterUser() {
        // given
        UserDto userDto = new UserDto(1, "홍길동", "chan@example.com", "password123", UserType.REGULAR);
        HttpEntity<UserDto> request = new HttpEntity<>(userDto);

        // when
        ResponseEntity<String> response = restTemplate.postForEntity("/register", request, String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("회원가입 성공 홍길동");
    }

    @Test
    public void testReservationController_CompleteReservation() {
        // given
        SeatSelectionDto reservationDto = new SeatSelectionDto(1, 2, 3, new int[]{101, 102, 103});
        HttpEntity<SeatSelectionDto> request = new HttpEntity<>(reservationDto);

        // when
        ResponseEntity<String> response = restTemplate.postForEntity("/reservation/complete", request, String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("예매 성공");
    }

    @Test
    public void testScheduleController_GetSchedules() {
        // when
        ResponseEntity<ScheduleDto[]> response = restTemplate.getForEntity("/schedules", ScheduleDto[].class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<ScheduleDto> schedules = Arrays.asList(response.getBody());
        assertThat(schedules).isNotEmpty();
        assertThat(schedules.get(0).getMatchup()).contains("한화 vs 두산");
    }

    @Test
    public void testSeatSectionController_GetSeatSections() {
        // when
        ResponseEntity<SeatSectionDto[]> response = restTemplate.getForEntity("/seats/sections?scheduleId=1", SeatSectionDto[].class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<SeatSectionDto> seatSections = Arrays.asList(response.getBody());
        assertThat(seatSections).isNotEmpty();
        assertThat(seatSections.get(0).getSectionName()).isEqualTo("1st base");
    }

    @Test
    public void testSeatSelectionController_AutoAssignSeats() {
        // given
        SeatSelectionDto seatSelectionDto = new SeatSelectionDto(1, 2, 3, null);
        HttpEntity<SeatSelectionDto> request = new HttpEntity<>(seatSelectionDto);

        // when
        ResponseEntity<String> response = restTemplate.postForEntity("/seats/auto-assign", request, String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("자동 좌석 선택 완료");
    }
}

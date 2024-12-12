package com.bticketing.main;

import com.bticketing.main.dto.SeatDto;
import com.bticketing.main.entity.SeatReservation;
import com.bticketing.main.repository.redis.SeatRedisRepository;
import com.bticketing.main.repository.seat.SeatRepository;
import com.bticketing.main.repository.seat.SeatReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AutoAssignSeatIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private SeatReservationRepository seatReservationRepository;

    @Autowired
    private SeatRedisRepository redisRepository;

    private RestTemplate restTemplate;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        // RestTemplate 초기화
        restTemplate = new RestTemplate();
        baseUrl = "http://localhost:" + port + "/seats";

        // DB 초기화
        redisRepository.clear();
        seatReservationRepository.deleteAll();

        // 좌석 상태 확인
        System.out.println("[SETUP] DB 상태 확인: " + seatRepository.findAll());
    }

    @Test
    void testAutoAssignSeats_RedisAndDbEmpty() {
        // Scenario 1: Redis와 DB 모두 비어있는 경우
        int scheduleId = 1;
        int numSeats = 3;

        // 초기 상태 확인
        for (int i = 1; i <= numSeats; i++) {
            int seatId = i;
            assertThat(redisRepository.getSeatStatus("seat:" + scheduleId + ":" + seatId)).isNull();
            assertThat(seatReservationRepository.findBySeatAndSchedule(seatId, scheduleId)).isEmpty();
        }

        // 요청 보내기
        String url = baseUrl + "/auto-assign?scheduleId=" + scheduleId + "&numSeats=" + numSeats;
        ResponseEntity<List<SeatDto>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                null,
                new ParameterizedTypeReference<List<SeatDto>>() {}
        );

        // 응답 검증
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<SeatDto> assignedSeats = response.getBody();
        assertThat(assignedSeats).isNotNull();
        assertThat(assignedSeats.size()).isEqualTo(numSeats);

        // 좌석 상태 검증
        for (SeatDto seatDto : assignedSeats) {
            int seatId = seatDto.getSeatId();

            // 상태 확인
            assertThat(seatDto.getStatus()).isEqualTo("RESERVED");

            // Redis 확인
            String redisStatus = redisRepository.getSeatStatus("seat:" + scheduleId + ":" + seatId);
            assertThat(redisStatus).isEqualTo("RESERVED");

            // DB 확인
            SeatReservation reservation = seatReservationRepository.findBySeatAndSchedule(seatId, scheduleId)
                    .orElseThrow(() -> new AssertionError("DB에 예약 정보가 존재하지 않습니다."));
            assertThat(reservation.getStatus()).isEqualTo("RESERVED");
        }
    }

    @Test
    void testAutoAssignSeats_RedisEmptyDbAvailable() {
        // Scenario 2: Redis에 데이터가 없고 DB에 "AVAILABLE" 상태로 데이터가 있는 경우
        int scheduleId = 1;
        int numSeats = 3;

        // DB에 "AVAILABLE" 상태로 좌석 추가
        for (int i = 1; i <= numSeats; i++) {
            SeatReservation reservation = new SeatReservation();
            reservation.setSeat(seatRepository.findById(i).orElseThrow());
            reservation.setScheduleId(scheduleId);
            reservation.setStatus("AVAILABLE");
            seatReservationRepository.save(reservation);
        }

        // 초기 상태 확인
        for (int i = 1; i <= numSeats; i++) {
            int seatId = i;
            assertThat(redisRepository.getSeatStatus("seat:" + scheduleId + ":" + seatId)).isNull();
            assertThat(seatReservationRepository.findBySeatAndSchedule(seatId, scheduleId)).isPresent();
        }

        // 요청 보내기
        String url = baseUrl + "/auto-assign?scheduleId=" + scheduleId + "&numSeats=" + numSeats;
        ResponseEntity<List<SeatDto>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                null,
                new ParameterizedTypeReference<List<SeatDto>>() {}
        );

        // 응답 검증
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<SeatDto> assignedSeats = response.getBody();
        assertThat(assignedSeats).isNotNull();
        assertThat(assignedSeats.size()).isEqualTo(numSeats);

        // 좌석 상태 검증
        for (SeatDto seatDto : assignedSeats) {
            int seatId = seatDto.getSeatId();

            // 상태 확인
            assertThat(seatDto.getStatus()).isEqualTo("RESERVED");

            // Redis 확인
            String redisStatus = redisRepository.getSeatStatus("seat:" + scheduleId + ":" + seatId);
            assertThat(redisStatus).isEqualTo("RESERVED");

            // DB 확인
            SeatReservation reservation = seatReservationRepository.findBySeatAndSchedule(seatId, scheduleId)
                    .orElseThrow(() -> new AssertionError("DB에 예약 정보가 존재하지 않습니다."));
            assertThat(reservation.getStatus()).isEqualTo("RESERVED");
        }
    }

    @Test
    void testAutoAssignSeats_AlreadyReserved() {
        // Scenario 3: 좌석이 이미 Redis와 DB에서 "RESERVED" 상태인 경우
        int scheduleId = 1;
        int numSeats = 3;

        // Redis와 DB에 "RESERVED" 상태로 데이터 추가
        for (int i = 1; i <= numSeats; i++) {
            SeatReservation reservation = new SeatReservation();
            reservation.setSeat(seatRepository.findById(i).orElseThrow());
            reservation.setScheduleId(scheduleId);
            reservation.setStatus("RESERVED");
            seatReservationRepository.save(reservation);

            redisRepository.setSeatStatus("seat:" + scheduleId + ":" + i, "RESERVED", 300);
        }

        // 요청 보내기
        String url = baseUrl + "/auto-assign?scheduleId=" + scheduleId + "&numSeats=" + numSeats;
        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                null,
                String.class
        );

        // 응답 검증
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("요청한 좌석 수를 자동 배정할 수 없습니다.");
    }

    @Test
    void testAutoAssignSeats_NotEnoughSeatsAvailable() {
        // Scenario 4: 좌석이 부족한 경우
        int scheduleId = 1;
        int numSeats = 5;

        // 일부 좌석만 "AVAILABLE" 상태로 추가
        for (int i = 1; i <= 3; i++) {
            SeatReservation reservation = new SeatReservation();
            reservation.setSeat(seatRepository.findById(i).orElseThrow());
            reservation.setScheduleId(scheduleId);
            reservation.setStatus("AVAILABLE");
            seatReservationRepository.save(reservation);
        }

        // 요청 보내기
        String url = baseUrl + "/auto-assign?scheduleId=" + scheduleId + "&numSeats=" + numSeats;
        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                null,
                String.class
        );

        // 응답 검증
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("요청한 좌석 수를 자동 배정할 수 없습니다.");
    }
}

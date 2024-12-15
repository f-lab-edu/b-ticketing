package com.bticketing.main;

import com.bticketing.main.dto.SeatDto;
import com.bticketing.main.entity.Seat;
import com.bticketing.main.entity.SeatReservation;
import com.bticketing.main.repository.redis.SeatRedisRepository;
import com.bticketing.main.repository.seat.SeatRepository;
import com.bticketing.main.repository.seat.SeatReservationRepository;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "logging.level.root=WARN",
                "logging.level.org.hibernate=INFO",
                "logging.level.org.springframework=WARN",
                "logging.level.com.bticketing.main=DEBUG"
        }
)
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
        // 테스트 대상 변수 설정
        int scheduleId = 1;
        int numSeats = 3;

        // 1. Redis와 DB 초기 상태 확인
        for (int i = 1; i <= numSeats; i++) {
            int seatId = i;

            // Redis가 비어 있는지 확인
            assertThat(redisRepository.getSeatStatus("seat:" + scheduleId + ":" + seatId))
                    .as("Redis에 seatId=%d에 대한 상태가 null이어야 합니다.", seatId)
                    .isNull();

            // DB가 비어 있는지 확인
            assertThat(seatReservationRepository.findBySeatAndSchedule(seatId, scheduleId))
                    .as("DB에 seatId=%d에 대한 예약 정보가 없어야 합니다.", seatId)
                    .isEmpty();
        }

        // 2. API 요청
        String url = baseUrl + "/auto-assign?scheduleId=" + scheduleId + "&numSeats=" + numSeats;
        ResponseEntity<List<SeatDto>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                null,
                new ParameterizedTypeReference<List<SeatDto>>() {}
        );

        // 3. 응답 상태 코드 확인
        assertThat(response.getStatusCode())
                .as("응답 상태 코드가 200 OK이어야 합니다.")
                .isEqualTo(HttpStatus.OK);

        // 4. 응답 Body 검증
        List<SeatDto> assignedSeats = response.getBody();
        assertThat(assignedSeats)
                .as("응답 Body가 null이어서는 안 됩니다.")
                .isNotNull();
        assertThat(assignedSeats.size())
                .as("할당된 좌석 수가 요청한 좌석 수와 일치해야 합니다.")
                .isEqualTo(numSeats);

        // 5. 좌석 상태 검증
        for (SeatDto seatDto : assignedSeats) {
            int seatId = seatDto.getSeatId();

            // 응답에서 상태 확인
            assertThat(seatDto.getStatus())
                    .as("SeatDto에서 seatId=%d의 상태는 RESERVED여야 합니다.", seatId)
                    .isEqualTo("RESERVED");

            // Redis 상태 확인
            String redisStatus = redisRepository.getSeatStatus("seat:" + scheduleId + ":" + seatId);
            assertThat(redisStatus)
                    .as("Redis에서 seatId=%d의 상태는 RESERVED여야 합니다.", seatId)
                    .isEqualTo("RESERVED");

            // DB 상태 확인
            SeatReservation reservation = seatReservationRepository.findBySeatAndSchedule(seatId, scheduleId)
                    .orElseThrow(() -> new AssertionError(
                            String.format("DB에 seatId=%d의 예약 정보가 존재하지 않습니다.", seatId)
                    ));
            assertThat(reservation.getStatus())
                    .as("DB에서 seatId=%d의 상태는 RESERVED여야 합니다.", seatId)
                    .isEqualTo("RESERVED");
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
        int numSeats = 8;
        int allSeats = 44;

        // Redis와 DB에 "RESERVED" 상태로 데이터 추가
        for (int i = 1; i <= allSeats; i++) {
            SeatReservation reservation = new SeatReservation();
            reservation.setSeat(seatRepository.findById(i).orElseThrow());
            reservation.setScheduleId(scheduleId);
            reservation.setStatus("RESERVED");
            seatReservationRepository.save(reservation);

            redisRepository.setSeatStatus("seat:" + scheduleId + ":" + i, "RESERVED", 300);
        }

        // 요청 보내기
        String url = baseUrl + "/auto-assign?scheduleId=" + scheduleId + "&numSeats=" + numSeats;
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    null,
                    String.class
            );
        } catch (HttpClientErrorException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(ex.getResponseBodyAsString()).contains("요청한 좌석 수를 자동 배정할 수 없습니다.");
        }
    }
    @Transactional
    @Test
    void testAutoAssignSeats_WhenRedisAvailableButDBEmpty() {
        int scheduleId = 1;
        int numSeats = 3;

        for (int i = 1; i <= 44; i++) {
            Seat seat = seatRepository.findById(i)
                    .orElseThrow(() -> new RuntimeException("Seat not found"));
            seat.toString(); // Lazy 로딩 초기화

            SeatReservation reservation = new SeatReservation();
            reservation.setSeat(seat);
            reservation.setScheduleId(scheduleId);
            reservation.setStatus("RESERVED");
            seatReservationRepository.save(reservation);

            redisRepository.setSeatStatus("seat:" + scheduleId + ":" + i, "RESERVED", 300);
        }

        redisRepository.setSeatStatus("seat:" + scheduleId + ":45", "AVAILABLE", 300);
        redisRepository.setSeatStatus("seat:" + scheduleId + ":46", "AVAILABLE", 300);


        // 요청 보내기
        String url = baseUrl + "/auto-assign?scheduleId=" + scheduleId + "&numSeats=" + numSeats;
        ResponseEntity<List<SeatDto>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                null,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<SeatDto> assignedSeats = response.getBody();
        assertThat(assignedSeats).isNotNull();
        assertThat(assignedSeats.size()).isEqualTo(numSeats);

        List<Integer> assignedSeatIds = assignedSeats.stream()
                .map(SeatDto::getSeatId)
                .collect(Collectors.toList());
        assertThat(assignedSeatIds).containsExactly(45, 46, 47);
    }
}

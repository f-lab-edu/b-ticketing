package com.bticketing.main;

import com.bticketing.main.dto.SeatDto;
import com.bticketing.main.entity.Seat;
import com.bticketing.main.entity.SeatReservation;
import com.bticketing.main.repository.redis.SeatRedisRepository;
import com.bticketing.main.repository.seat.SeatRepository;
import com.bticketing.main.repository.seat.SeatReservationRepository;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class SelectSeatIntegrationTest {

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
        restTemplate = new RestTemplate(); // RestTemplate 직접 초기화
        baseUrl = "http://localhost:" + port + "/seats";  // Base URL 설정

        // DB 초기화 로직 제거
        redisRepository.clear();
        seatReservationRepository.deleteAll(); // 예약 정보만 초기화

        // 좌석 데이터는 DB에 이미 삽입되어 있으므로 확인만 수행
        System.out.println("[SETUP] DB 상태 확인: " + seatRepository.findAll());
    }


    @Test
    void testSelectSeat_NoDataInRedisAndDb() {
        // Scenario: Redis와 DB 모두 데이터가 없는 경우
        int scheduleId = 1;
        int seatId = 1;

        // 초기 상태 확인
        assertThat(redisRepository.getSeatStatus("seat:" + scheduleId + ":" + seatId)).isNull();
        assertThat(seatReservationRepository.findBySeatAndSchedule(seatId, scheduleId)).isEmpty();

        // selectSeat 호출
        String url = baseUrl + "/select?scheduleId=" + scheduleId + "&seatId=" + seatId;
        ResponseEntity<SeatDto> response = restTemplate.postForEntity(url, null, SeatDto.class);

        // Redis 상태 확인
        String redisStatus = redisRepository.getSeatStatus("seat:" + scheduleId + ":" + seatId);
        assertThat(redisStatus).isEqualTo("RESERVED");

        // DB 상태 확인
        SeatReservation reservation = seatReservationRepository.findBySeatAndSchedule(seatId, scheduleId)
                .orElseThrow(() -> new AssertionError("DB에 예약 정보가 생성되지 않았습니다."));
        assertThat(reservation.getStatus()).isEqualTo("RESERVED");

        // 응답 확인
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSeatId()).isEqualTo(seatId);
        assertThat(response.getBody().getStatus()).isEqualTo("RESERVED");
    }

    @Test
    void testSelectSeat_NoDataInRedisDbHasAvailable() {
        // Scenario 2: Redis에 데이터가 없고 DB에 AVAILABLE 상태로 데이터가 있는 경우
        int scheduleId = 1;
        int seatId = 2;

        // DB에 AVAILABLE 상태로 데이터 추가
        Seat seat = seatRepository.findById(seatId).orElseThrow();
        seatReservationRepository.save(new SeatReservation(1, seat, scheduleId, "AVAILABLE"));

        String url = baseUrl + "/select?scheduleId=" + scheduleId + "&seatId=" + seatId;

        ResponseEntity<SeatDto> response = restTemplate.postForEntity(url, null, SeatDto.class);

        // Redis에 RESERVED 상태가 기록되었는지 확인
        String redisStatus = redisRepository.getSeatStatus("seat:" + scheduleId + ":" + seatId);
        assertThat(redisStatus).isEqualTo("RESERVED");

        // DB에 RESERVED 상태로 업데이트되었는지 확인
        SeatReservation reservation = seatReservationRepository.findBySeatAndSchedule(seatId, scheduleId)
                .orElseThrow(() -> new AssertionError("DB에 예약 정보가 존재하지 않습니다."));
        assertThat(reservation.getStatus()).isEqualTo("RESERVED");

        // 응답 검증
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSeatId()).isEqualTo(seatId);
        assertThat(response.getBody().getStatus()).isEqualTo("RESERVED");
    }

    @Test
    void testSelectSeat_AlreadyReservedInBothRedisAndDb() {
        // Scenario 3: Redis와 DB 모두 RESERVED 상태인 경우
        int scheduleId = 1;
        int seatId = 3;

        // Redis와 DB에 RESERVED 상태로 데이터 추가
        Seat seat = seatRepository.findById(seatId).orElseThrow();
        seatReservationRepository.save(new SeatReservation(1, seat, scheduleId, "RESERVED"));
        redisRepository.setSeatStatus("seat:" + scheduleId + ":" + seatId, "RESERVED", 300);

        String url = baseUrl + "/select?scheduleId=" + scheduleId + "&seatId=" + seatId;

        ResponseEntity<String> response = restTemplate.postForEntity(url, null, String.class);

        // 응답 검증
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("이미 예약된 좌석입니다.");
    }

    @Test
    void testSelectSeat_ReservedInRedisAvailableInDb() {
        // Scenario 4: Redis에 RESERVED 상태이고 DB에 AVAILABLE 상태인 경우
        int scheduleId = 1;
        int seatId = 4;

        // Redis와 DB에 데이터 추가
        Seat seat = seatRepository.findById(seatId).orElseThrow();
        seatReservationRepository.save(new SeatReservation(1, seat, scheduleId, "AVAILABLE"));
        redisRepository.setSeatStatus("seat:" + scheduleId + ":" + seatId, "RESERVED", 300);

        String url = baseUrl + "/select?scheduleId=" + scheduleId + "&seatId=" + seatId;

        ResponseEntity<String> response = restTemplate.postForEntity(url, null, String.class);

        // 응답 검증
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("이미 예약된 좌석입니다.");
    }
}

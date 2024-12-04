package com.bticketing.main;

import com.bticketing.main.dto.SeatDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class SeatControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String REDIS_KEY_PREFIX = "seat";

    @BeforeEach
    void setUp() {
        initializeDatabase();
        initializeRedis();
    }

    private void initializeDatabase() {
        // DB 데이터 초기화 및 기본 데이터 세팅
        jdbcTemplate.execute("DELETE FROM seat_reservation");
        jdbcTemplate.execute("DELETE FROM seat");

        jdbcTemplate.execute("INSERT INTO seat (seat_id, seat_row, seat_number) VALUES (1, 'A', 1)");
        jdbcTemplate.execute("INSERT INTO seat (seat_id, seat_row, seat_number) VALUES (2, 'A', 2)");
        jdbcTemplate.execute("INSERT INTO seat (seat_id, seat_row, seat_number) VALUES (3, 'A', 3)");
        jdbcTemplate.execute("INSERT INTO seat (seat_id, seat_row, seat_number) VALUES (4, 'A', 4)");
        jdbcTemplate.execute("INSERT INTO seat (seat_id, seat_row, seat_number) VALUES (5, 'B', 1)");
        jdbcTemplate.execute("INSERT INTO seat (seat_id, seat_row, seat_number) VALUES (6, 'B', 2)");
        jdbcTemplate.execute("INSERT INTO seat (seat_id, seat_row, seat_number) VALUES (7, 'B', 3)");
        jdbcTemplate.execute("INSERT INTO seat (seat_id, seat_row, seat_number) VALUES (8, 'B', 4)");


        // 좌석 1은 이미 COMPLETED 상태로 예약
        jdbcTemplate.execute("INSERT INTO seat_reservation (reservation_id, seat_id, schedule_id, status) VALUES (1, 1, 1, 'COMPLETED')");
    }

    private void initializeRedis() {
        // Redis 데이터 초기화
        redisTemplate.getConnectionFactory().getConnection().flushDb();

        // Redis에 좌석 2를 RESERVED 상태로 설정
        redisTemplate.opsForValue().set(redisKey(1, 2), "RESERVED", 300, TimeUnit.SECONDS);
    }

    private String redisKey(int scheduleId, int seatId) {
        return String.format("%s:%d:%d", REDIS_KEY_PREFIX, scheduleId, seatId);
    }

    @Test
    public void testSelectSeat_Success() throws Exception {
        // Redis에서 좌석 상태 초기 확인 (좌석 키가 존재하지 않아야 함)
        assertFalse(redisTemplate.hasKey(redisKey(1, 3)));

        // /seats/select 호출
        mockMvc.perform(post("/seats/select")
                        .param("scheduleId", "1")
                        .param("seatId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatId").value(3))
                .andExpect(jsonPath("$.status").value("RESERVED"));

        // Redis 상태 변경 확인 (좌석 키가 생성되어야 함)
        assertTrue(redisTemplate.hasKey(redisKey(1, 3)));
    }

    @Test
    public void testSelectSeat_AlreadyReserved() throws Exception {
        // Redis에 좌석 상태를 RESERVED로 설정
        redisTemplate.opsForValue().set(redisKey(1, 2), "RESERVED", 300, TimeUnit.SECONDS);

        // 이미 예약된 좌석을 선택할 경우
        mockMvc.perform(post("/seats/select")
                        .param("scheduleId", "1")
                        .param("seatId", "2"))
                .andExpect(status().isConflict())// 409 상태 코드
                .andExpect(jsonPath("$.status").value("이미 예약된 좌석입니다.")); // 에러 메시지 확인
    }

    @Test
    public void testAutoAssignSeats_Success() throws Exception {
        System.out.println("testAutoAssignSeats_Success");
        mockMvc.perform(post("/seats/auto-assign")
                        .param("scheduleId", "1")
                        .param("numSeats", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].seatId").value(5))
                .andExpect(jsonPath("$[0].status").value("RESERVED"))
                .andExpect(jsonPath("$[1].seatId").value(6))
                .andExpect(jsonPath("$[1].status").value("RESERVED"))
                .andExpect(jsonPath("$[2].seatId").value(7))
                .andExpect(jsonPath("$[2].status").value("RESERVED"));


        assertTrue(redisTemplate.hasKey(redisKey(1, 5)));
        assertTrue(redisTemplate.hasKey(redisKey(1, 6)));
        assertTrue(redisTemplate.hasKey(redisKey(1, 7)));
    }


    @Test
    public void testAutoAssignSeats_NotAvailable() throws Exception {
        // 요청한 좌석 수를 자동 배정할 수 없는 경우
        mockMvc.perform(post("/seats/auto-assign")
                        .param("scheduleId", "1")
                        .param("numSeats", "6")) // 좌석이 부족한 요청
                .andExpect(status().isBadRequest()) // 400 상태 코드
                .andExpect(content().string("요청한 좌석 수를 자동 배정할 수 없습니다."));
    }

    @Test
    public void testGetSeatsStatus_Success() throws Exception {
        setUp();
        mockMvc.perform(get("/seats/status")
                        .param("scheduleId", "1"))
                .andExpect(status().isOk()) // HTTP 200 응답 확인
                .andExpect(jsonPath("$[?(@.seatId == 1)].status").value("COMPLETED")) // 좌석 1 상태 확인
                .andExpect(jsonPath("$[?(@.seatId == 2)].status").value("RESERVED")) // 좌석 2 상태 확인
                .andExpect(jsonPath("$[?(@.seatId == 3)]").doesNotExist()) // 예약되지 않은 좌석은 반환되지 않음
                .andExpect(jsonPath("$[?(@.seatId == 4)]").doesNotExist()) // 예약되지 않은 좌석은 반환되지 않음
                .andExpect(jsonPath("$[?(@.seatId == 5)]").doesNotExist()); // 예약되지 않은 좌석은 반환되지 않음
    }

}

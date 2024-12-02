package com.bticketing.main.service;

import com.bticketing.main.dto.SeatDto;
import com.bticketing.main.entity.Seat;
import com.bticketing.main.repository.redis.SeatRedisRepository;
import com.bticketing.main.repository.seat.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
public class RedisDatabaseSyncTest {

    @Autowired
    private SeatService seatService;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private SeatRedisRepository redisRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setupTestData() {
        // 데이터베이스 초기화
        Seat seat = new Seat(1, 1, "AVAILABLE");
        seatRepository.save(seat);

        // Redis 초기화
        String redisKey = "seat:" + seat.getSeatId();
        redisRepository.setSeatStatus(redisKey, "AVAILABLE", 300);
    }

    @Test
    @Transactional
    void testRedisAndDatabaseSync() {
        int testSeatId = 1;
        String redisKey = "seat:" + testSeatId;

        // Seat 선택 (서비스 호출)
        SeatDto selectedSeat = seatService.selectSeat(testSeatId);

        // Redis 상태 확인
        String redisStatus = redisRepository.getSeatStatus(redisKey);
        assertNotNull(redisStatus, "Redis에서 좌석 상태를 찾을 수 없습니다.");
        assertEquals("SELECTED", redisStatus, "Redis 좌석 상태가 올바르지 않습니다.");

        // 데이터베이스 상태 확인
        Optional<Seat> optionalSeat = seatRepository.findById(testSeatId);
        assertNotNull(optionalSeat, "데이터베이스에서 좌석을 찾을 수 없습니다.");
        Seat seat = optionalSeat.orElseThrow(() -> new RuntimeException("좌석이 존재하지 않습니다."));
        assertEquals("SELECTED", seat.getStatus(), "데이터베이스 좌석 상태가 올바르지 않습니다.");

        // Redis와 데이터베이스 상태 동기화 확인
        assertEquals(redisStatus, seat.getStatus(), "Redis와 데이터베이스 상태가 일치하지 않습니다.");
    }
}

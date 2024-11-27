package com.bticketing.main.service;

import com.bticketing.main.entity.Seat;
import com.bticketing.main.repository.RedisRepository;
import com.bticketing.main.repository.RedisSeatRepository;
import com.bticketing.main.repository.SeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SeatService {

    private final SeatRepository seatRepository;
    private final RedisSeatRepository redisRepository;

    public SeatService(SeatRepository seatRepository, RedisSeatRepository redisRepository) {
        this.seatRepository = seatRepository;
        this.redisRepository = redisRepository;
    }

    @Transactional
    public boolean selectSeat(int seatId) {
        String lockKey = "seat:lock:" + seatId;
        String seatKey = "seat:" + seatId;

        return redisRepository.executeWithLock(lockKey, 5, () -> {
            // Redis에서 좌석 상태 확인
            String status = redisRepository.getSeatStatus(seatKey);
            if (status != null && !status.equals("AVAILABLE")) {
                throw new RuntimeException("이미 선택된 좌석입니다.");
            }

            // 데이터베이스에서 좌석 정보 확인
            Seat seat = seatRepository.findById(seatId)
                    .orElseThrow(() -> new RuntimeException("해당 좌석을 찾을 수 없습니다."));
            if (!seat.getStatus().equals("AVAILABLE")) {
                throw new RuntimeException("이미 예약된 좌석입니다.");
            }

            // Redis에 좌석 상태 설정 및 DB 업데이트
            redisRepository.setSeatStatus(seatKey, "SELECTED", 300);
            seat.setStatus("SELECTED");
            seatRepository.save(seat);

            return true; // 작업 결과 반환
        });
    }

    public List<Seat> autoAssignSeats(int scheduleId, int sectionId, int numSeats) {
        String lockKey = "autoAssign:lock:" + scheduleId + ":" + sectionId;

        return redisRepository.executeWithLock(lockKey, 5, () -> {
            // 요청한 일정 및 구역의 사용 가능한 좌석 조회
            List<Seat> availableSeats = seatRepository.findByScheduleIdAndSectionId(scheduleId, sectionId);
            if (availableSeats.size() < numSeats) {
                throw new RuntimeException("사용 가능한 좌석이 부족합니다.");
            }

            // 연속된 좌석 할당
            List<Seat> assignedSeats = availableSeats.subList(0, numSeats);
            for (Seat seat : assignedSeats) {
                redisRepository.setSeatStatus("seat:" + seat.getSeatId(), "SELECTED", 300);
            }

            return assignedSeats; // 작업 결과 반환
        });
    }
}

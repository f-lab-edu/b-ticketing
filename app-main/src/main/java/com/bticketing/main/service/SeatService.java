package com.bticketing.main.service;

import com.bticketing.main.dto.SeatDto;
import com.bticketing.main.entity.Seat;
import com.bticketing.main.repository.redis.SeatRedisRepository;
import com.bticketing.main.repository.seat.SeatRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SeatService {

    private final SeatRepository seatRepository;
    private final SeatRedisRepository redisRepository;
    private final StringRedisTemplate redisTemplate;

    public SeatService(SeatRepository seatRepository, SeatRedisRepository redisRepository, StringRedisTemplate redisTemplate) {
        this.seatRepository = seatRepository;
        this.redisRepository = redisRepository;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public SeatDto selectSeat(int seatId) {
        String lockKey = "seat:lock:" + seatId;
        String seatKey = "seat:" + seatId;

        return redisRepository.executeWithLock(lockKey, 5, () -> {
            String status = redisRepository.getSeatStatus(seatKey);
            if (status != null && !status.equals("AVAILABLE")) {
                throw new RuntimeException("이미 선택된 좌석입니다.");
            }

            Seat seat = seatRepository.findById(seatId)
                    .orElseThrow(() -> new RuntimeException("해당 좌석을 찾을 수 없습니다."));
            if (!seat.getStatus().equals("AVAILABLE")) {
                throw new RuntimeException("이미 예약된 좌석입니다.");
            }

            redisRepository.setSeatStatus(seatKey, "SELECTED", 300);
            seat.setStatus("SELECTED");
            seatRepository.save(seat);

            // Redis Pub/Sub 메시지 발행
            publishSeatUpdate(seatId, "SELECTED");

            // 엔티티를 DTO로 변환하여 반환
            return new SeatDto(seat.getSeatId(), seat.getStatus());
        });
    }

    public List<SeatDto> autoAssignSeats(int scheduleId, int sectionId, int numSeats) {
        String lockKey = "autoAssign:lock:" + scheduleId + ":" + sectionId;

        return redisRepository.executeWithLock(lockKey, 5, () -> {
            List<Seat> availableSeats = seatRepository.findByScheduleIdAndSectionId(scheduleId, sectionId);
            if (availableSeats.size() < numSeats) {
                throw new RuntimeException("사용 가능한 좌석이 부족합니다.");
            }

            List<Seat> assignedSeats = availableSeats.subList(0, numSeats);
            for (Seat seat : assignedSeats) {
                redisRepository.setSeatStatus("seat:" + seat.getSeatId(), "SELECTED", 300);
                publishSeatUpdate(seat.getSeatId(), "SELECTED");
                seat.setStatus("SELECTED");
                seatRepository.save(seat);
            }

            // 엔티티를 DTO로 변환하여 반환
            return assignedSeats.stream()
                    .map(seat -> new SeatDto(seat.getSeatId(), seat.getStatus()))
                    .toList();
        });
    }

    private void publishSeatUpdate(int seatId, String status) {
        String message = String.format("{\"seatId\":%d,\"status\":\"%s\"}", seatId, status);
        redisTemplate.convertAndSend("seat:updates", message);
    }
}

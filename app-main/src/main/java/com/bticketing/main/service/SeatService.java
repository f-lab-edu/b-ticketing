package com.bticketing.main.service;

import com.bticketing.main.dto.SeatDto;
import com.bticketing.main.entity.Seat;
import com.bticketing.main.entity.SeatReservation;
import com.bticketing.main.repository.redis.SeatRedisRepository;
import com.bticketing.main.repository.seat.SeatReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SeatService {

    private final SeatRedisRepository redisRepository;
    private final SeatReservationRepository seatReservationRepository;

    public SeatService( SeatRedisRepository redisRepository, SeatReservationRepository seatReservationRepository) {

        this.redisRepository = redisRepository;
        this.seatReservationRepository = seatReservationRepository;
    }

    @Transactional
    public SeatDto selectSeat(int scheduleId, int seatId) {
        // Redis에서 스케줄별로 고유한 좌석 키 생성
        String lockKey = "seat:lock:" + scheduleId + ":" + seatId;
        String seatKey = "seat:" + scheduleId + ":" + seatId;

        // Redis에서 현재 좌석 상태 확인
        if ("RESERVED".equals(redisRepository.getSeatStatus(seatKey))) {
            throw new RuntimeException("이미 예약된 좌석입니다.");
        }

        return redisRepository.executeWithLock(lockKey, 5, () -> {
            String status = redisRepository.getSeatStatus(seatKey);

            // 이미 예약 상태인 경우 예외 처리
            if (status != null && "RESERVED".equals(status)) {
                throw new RuntimeException("이미 예약된 좌석입니다.");
            }

            // 좌석 상태를 RESERVED로 변경하고 TTL 설정 (300초)
            redisRepository.setSeatStatus(seatKey, "RESERVED", 300);
            return new SeatDto(seatId, "RESERVED");
        });
    }

    public List<SeatDto> autoAssignSeats(int scheduleId, int numSeats) {
        // 1. 모든 좌석을 DB에서 가져오고, 열(row)별로 정렬
        List<SeatReservation> seatReservations = seatReservationRepository.findByScheduleId(scheduleId);
        List<Seat> allSeats = seatReservations.stream()
                .map(SeatReservation::getSeat)
                .sorted((seat1, seat2) -> {
                    int rowComparison = seat1.getSeatRow().compareTo(seat2.getSeatRow());
                    return (rowComparison != 0) ? rowComparison : Integer.compare(seat1.getSeatNumber(), seat2.getSeatNumber());
                })
                .toList();

        // 2. Redis에서 예약된 좌석 상태 조회
        Map<String, String> reservedSeats = redisRepository.getAllReservedSeats(scheduleId);
        Set<Integer> reservedSeatIds = reservedSeats.keySet().stream()
                .map(key -> Integer.parseInt(key.replace("seat:" + scheduleId + ":", "")))
                .collect(Collectors.toSet());

        // 3. DB에서 완료된 좌석 상태 가져오기
        Set<Integer> completedSeatIds = seatReservations.stream()
                .filter(reservation -> "COMPLETED".equals(reservation.getStatus()))
                .map(reservation -> reservation.getSeat().getSeatId())
                .collect(Collectors.toSet());

        // 4. Redis와 DB 예약 좌석 상태 결합
        reservedSeatIds.addAll(completedSeatIds);

        // 5. 연속된 빈 좌석 탐색 로직
        List<SeatDto> assignedSeats;
        List<Seat> currentRowSeats = new ArrayList<>();
        String currentRow = null;

        for (Seat seat : allSeats) {
            // 현재 탐색 중인 열(row)이 바뀌면 처리
            if (!seat.getSeatRow().equals(currentRow)) {
                assignedSeats = findConsecutiveSeats(currentRowSeats, reservedSeatIds, numSeats);
                if (!assignedSeats.isEmpty()) {
                    // 새로 배정된 좌석을 Redis에 추가
                    assignedSeats.forEach(seatDto -> {
                        String seatKey = "seat:" + scheduleId + ":" + seatDto.getSeatId();
                        redisRepository.setSeatStatus(seatKey, "RESERVED", 300); // 5분 TTL
                    });
                    return assignedSeats;
                }
                currentRowSeats.clear(); // 현재 열 초기화
                currentRow = seat.getSeatRow(); // 새 열(row) 설정
            }
            currentRowSeats.add(seat);
        }

        // 마지막 열(row)에 대해서도 처리
        assignedSeats = findConsecutiveSeats(currentRowSeats, reservedSeatIds, numSeats);
        if (!assignedSeats.isEmpty()) {
            // 새로 배정된 좌석을 Redis에 추가
            assignedSeats.forEach(seatDto -> {
                String seatKey = "seat:" + scheduleId + ":" + seatDto.getSeatId();
                redisRepository.setSeatStatus(seatKey, "RESERVED", 300); // 5분 TTL
            });
            return assignedSeats;
        }

        throw new RuntimeException("요청한 좌석 수를 자동 배정할 수 없습니다.");
    }

    // 연속된 빈 좌석 탐색 메서드
    private List<SeatDto> findConsecutiveSeats(List<Seat> seats, Set<Integer> reservedSeatIds, int numSeats) {
        List<Seat> consecutiveSeats = new ArrayList<>();

        for (Seat seat : seats) {
            if (!reservedSeatIds.contains(seat.getSeatId())) {
                consecutiveSeats.add(seat);
                if (consecutiveSeats.size() == numSeats) {
                    return consecutiveSeats.stream()
                            .map(s -> new SeatDto(s.getSeatId(), "RESERVED"))
                            .collect(Collectors.toList());
                }
            } else {
                consecutiveSeats.clear(); // 연속 좌석이 끊기면 초기화
            }
        }
        return new ArrayList<>(); // 요청한 좌석 수를 만족하지 못하면 빈 리스트 반환
    }

    public List<SeatDto> getSeatsStatus(int scheduleId) {
        // Step 1: DB에서 해당 `scheduleId`의 결제 완료된 좌석 조회
        List<SeatReservation> completedReservations = seatReservationRepository.findByScheduleIdAndStatus(scheduleId, "COMPLETED");
        Set<Integer> completedSeatIds = completedReservations.stream()
                .map(reservation -> reservation.getSeat().getSeatId())
                .collect(Collectors.toSet());

        // Step 2: Redis에서 예약 중인 좌석 상태 조회
        Map<String, String> reservedSeats = redisRepository.getAllReservedSeats(scheduleId);

        // Step 3: Redis 및 DB 데이터를 결합하여 반환
        List<SeatDto> seatStatusList = new ArrayList<>();

        // Redis 데이터 추가 (RESERVED 상태)
        reservedSeats.forEach((key, value) -> {
            int seatId = Integer.parseInt(key.replace("seat:" + scheduleId + ":", ""));
            seatStatusList.add(new SeatDto(seatId, "RESERVED"));
        });

        // DB 데이터 추가 (COMPLETED 상태)
        completedSeatIds.forEach(seatId -> {
            seatStatusList.add(new SeatDto(seatId, "COMPLETED"));
        });

        return seatStatusList;
    }
}

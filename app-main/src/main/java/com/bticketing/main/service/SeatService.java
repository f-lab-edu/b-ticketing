package com.bticketing.main.service;

import com.bticketing.main.dto.SeatDto;
import com.bticketing.main.entity.Seat;
import com.bticketing.main.entity.SeatReservation;
import com.bticketing.main.exception.SeatAlreadyReservedException;
import com.bticketing.main.exception.SeatsNotAvailableException;
import com.bticketing.main.repository.redis.SeatRedisRepository;
import com.bticketing.main.repository.seat.SeatRepository;
import com.bticketing.main.repository.seat.SeatReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SeatService {

    private final SeatRedisRepository redisRepository;
    private final SeatRepository seatRepository;
    private final SeatReservationRepository seatReservationRepository;

    private static final int SEAT_RESERVATION_TTL = 300; // 예약 TTL (초)

    public SeatService(SeatRedisRepository redisRepository, SeatReservationRepository seatReservationRepository, SeatRepository seatRepository) {
        this.redisRepository = redisRepository;
        this.seatReservationRepository = seatReservationRepository;
        this.seatRepository = seatRepository;
    }

    @Transactional
    public SeatDto selectSeat(int scheduleId, int seatId) {
        // Redis 키 생성
        String lockKey = generateLockKey(scheduleId, seatId);
        String seatKey = generateSeatKey(scheduleId, seatId);

        // 좌석 상태 확인 및 예외 처리
        if (isSeatReserved(seatKey)) {
            throw new SeatAlreadyReservedException("이미 예약된 좌석입니다.");
        }

        // 락을 이용한 좌석 예약 처리
        return redisRepository.executeWithLock(lockKey, SEAT_RESERVATION_TTL, () -> {
            // 락 후 상태 확인 (다른 프로세스가 예약했을 수 있음)
            if (isSeatReserved(seatKey)) {
                throw new SeatAlreadyReservedException("이미 예약된 좌석입니다.");
            }
            // 좌석 상태를 RESERVED로 업데이트
            updateSeatStatusInRedis(scheduleId, seatId, "RESERVED");
            return new SeatDto(seatId, "RESERVED");
        });
    }

    public List<SeatDto> autoAssignSeats(int scheduleId, int numSeats) {
        // 가용 좌석 조회
        List<Seat> availableSeats = getAvailableSeats(scheduleId);
        System.out.println("Available seats: " + availableSeats);

        // 연속 좌석 찾기
        List<SeatDto> assignedSeats = findConsecutiveSeats(availableSeats, numSeats);
        System.out.println("Assigned seats: " + assignedSeats);

        if (assignedSeats.isEmpty()) {
            throw new SeatsNotAvailableException("요청한 좌석 수를 자동 배정할 수 없습니다.");
        }

        // 좌석 상태 업데이트
        assignedSeats.forEach(seat -> updateSeatStatusInRedis(scheduleId, seat.getSeatId(), "RESERVED"));
        return assignedSeats;
    }

    public List<SeatDto> getSeatsStatus(int scheduleId) {
        // Step 1: Redis와 DB 데이터를 병합하여 좌석 ID와 상태 정보를 가져옴
        Map<Integer, String> reservedAndCompletedSeatData = mergeReservationData(scheduleId);

        // Step 2: 병합된 데이터를 SeatDto 형태로 변환하여 반환
        return reservedAndCompletedSeatData.entrySet().stream()
                .map(entry -> new SeatDto(entry.getKey(), entry.getValue())) // SeatDto로 변환
                .toList();
    }

    // -------------------------------
    // Private Helper Methods
    // -------------------------------

    private boolean isSeatReserved(String seatKey) {
        // Redis에서 좌석 상태 확인
        String status = redisRepository.getSeatStatus(seatKey);
        System.out.println("Checking if seat reserved: " + seatKey + " -> " + status);
        return "RESERVED".equals(status);
    }

    private String generateLockKey(int scheduleId, int seatId) {
        String lockKey = String.format("seat:lock:%d:%d", scheduleId, seatId);
        System.out.println("Generated Lock Key: " + lockKey);
        return lockKey;
    }

    private String generateSeatKey(int scheduleId, int seatId) {
        String seatKey = String.format("seat:%d:%d", scheduleId, seatId);
        System.out.println("Generated Seat Key: " + seatKey);
        return seatKey;
    }

    private void updateSeatStatusInRedis(int scheduleId, int seatId, String status) {
        String seatKey = generateSeatKey(scheduleId, seatId);
        redisRepository.setSeatStatus(seatKey, status, SEAT_RESERVATION_TTL);
        System.out.println("Updated Redis Seat Status: " + seatKey + " -> " + status + " (TTL: " + SEAT_RESERVATION_TTL + ")");
    }

    public List<Seat> getAvailableSeats(int scheduleId) {
        // 병합된 예약된 좌석 데이터 가져오기
        Map<Integer, String> reservedSeatData = mergeReservationData(scheduleId);

        // 예약된 좌석 ID 추출
        Set<Integer> reservedSeatIds = reservedSeatData.keySet();
        System.out.println("Reserved Seat IDs: " + reservedSeatIds);

        // DB에서 모든 좌석 ID 조회
        List<Integer> allSeatIds = seatRepository.findAllSeatIds(); // 좌석 ID만 가져옴
        System.out.println("All Seat IDs: " + allSeatIds);

        // 예약되지 않은 좌석 ID 필터링
        List<Integer> availableSeatIds = allSeatIds.stream()
                .filter(seatId -> !reservedSeatIds.contains(seatId)) // 예약되지 않은 좌석만 선택
                .sorted() // 정렬 (ID만 정렬)
                .toList();

        System.out.println("Available Seat IDs: " + availableSeatIds);

        // 예약되지 않은 좌석 ID로 좌석 객체 조회
        List<Seat> availableSeats = seatRepository.findAllById(availableSeatIds).stream()
                .sorted(Comparator.comparing(Seat::getSeatRow).thenComparing(Seat::getSeatNumber)) // 좌석 정렬
                .toList();

        System.out.println("Available Seats: " + availableSeats);
        return availableSeats;
    }

    private List<SeatDto> findConsecutiveSeats(List<Seat> seats, int numSeats) {
        List<Seat> consecutiveSeats = new ArrayList<>();

        for (Seat seat : seats) {
            if (consecutiveSeats.isEmpty() || isNextSeat(consecutiveSeats.get(consecutiveSeats.size() - 1), seat)) {
                consecutiveSeats.add(seat);
                if (consecutiveSeats.size() == numSeats) {
                    System.out.println("Found consecutive seats: " + consecutiveSeats);
                    return convertToSeatDtos(consecutiveSeats);
                }
            } else {
                System.out.println("Resetting consecutive seats at seat: " + seat);
                consecutiveSeats.clear(); // 연속성이 끊기면 초기화
                consecutiveSeats.add(seat); // 현재 좌석부터 다시 시작
            }
        }
        System.out.println("No consecutive seats found.");
        return Collections.emptyList();
    }

    private boolean isNextSeat(Seat currentSeat, Seat nextSeat) {
        boolean result = currentSeat.getSeatRow().equals(nextSeat.getSeatRow()) &&
                currentSeat.getSeatNumber() + 1 == nextSeat.getSeatNumber();
        System.out.println("isNextSeat: Current=" + currentSeat + ", Next=" + nextSeat + ", Result=" + result);
        return result;
    }

    private List<SeatDto> convertToSeatDtos(List<Seat> seats) {
        List<SeatDto> seatDtos = seats.stream()
                .map(seat -> new SeatDto(seat.getSeatId(), "RESERVED"))
                .toList();
        System.out.println("Converted to SeatDtos: " + seatDtos);
        return seatDtos;
    }

    private Map<Integer, String> mergeReservationData(int scheduleId) {
        // Redis에서 예약된 좌석 조회 (seatId와 status를 포함한 데이터)
        Map<Integer, String> redisData = redisRepository.getAllReservedSeats(scheduleId).entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> Integer.parseInt(entry.getKey().replace("seat:" + scheduleId + ":", "")),
                        Map.Entry::getValue // 상태 값 그대로 유지
                ));
        System.out.println("Redis Data: " + redisData);

        // DB에서 COMPLETED 상태의 좌석 조회
        Map<Integer, String> dbData = seatReservationRepository.findByScheduleIdAndStatus(scheduleId, "COMPLETED").stream()
                .collect(Collectors.toMap(
                        reservation -> reservation.getSeat().getSeatId(),
                        reservation -> "COMPLETED"
                ));
        System.out.println("DB Data: " + dbData);

        // Redis 데이터와 DB 데이터를 병합 (새로운 Map 생성)
        Map<Integer, String> mergedData = new HashMap<>();
        mergedData.putAll(redisData); // Redis 데이터 추가
        dbData.forEach((key, value) -> mergedData.put(key, value)); // DB 데이터로 덮어쓰기
        System.out.println("Merged Data: " + mergedData);

        return mergedData; // 병합된 데이터 반환
    }
}

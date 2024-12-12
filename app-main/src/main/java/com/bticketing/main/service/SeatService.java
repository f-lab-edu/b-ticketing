package com.bticketing.main.service;

import com.bticketing.main.dto.SeatDto;
import com.bticketing.main.entity.Seat;
import com.bticketing.main.entity.SeatReservation;
import com.bticketing.main.exception.SeatAlreadyReservedException;
import com.bticketing.main.exception.SeatsNotAvailableException;
import com.bticketing.main.repository.redis.SeatRedisRepository;
import com.bticketing.main.repository.seat.SeatRepository;
import com.bticketing.main.repository.seat.SeatReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SeatService {

    private static final Logger logger = LoggerFactory.getLogger(SeatService.class);

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
        String lockKey = generateLockKey(scheduleId, seatId);
        String seatKey = generateSeatKey(scheduleId, seatId);

        // Redis 상태 확인
        String redisStatus = redisRepository.getSeatStatus(seatKey);
        System.out.println("[TEST] Redis 상태 확인: seatKey=" + seatKey + ", redisStatus=" + redisStatus);

        // DB 동기화
        if (redisStatus == null) {
            redisStatus = fetchAndSyncSeatStatus(scheduleId, seatId);
            System.out.println("[TEST] DB 동기화 후 Redis 상태: seatKey=" + seatKey + ", redisStatus=" + redisStatus);
        }

        if ("RESERVED".equals(redisStatus)) {
            System.out.println("[TEST] 좌석이 이미 예약됨: seatKey=" + seatKey + ", scheduleId=" + scheduleId + ", seatId=" + seatId);
            throw new SeatAlreadyReservedException("이미 예약된 좌석입니다.");
        }

        return redisRepository.executeWithLock(lockKey, SEAT_RESERVATION_TTL, () -> {
            System.out.println("[TEST] 락 획득 후 작업 실행: lockKey=" + lockKey);

            String currentStatus = redisRepository.getSeatStatus(seatKey);
            System.out.println("[TEST] 락 획득 후 Redis 상태 확인: seatKey=" + seatKey + ", currentStatus=" + currentStatus);

            if ("RESERVED".equals(currentStatus)) {
                System.out.println("[TEST] 좌석이 이미 예약됨: seatKey=" + seatKey + ", scheduleId=" + scheduleId + ", seatId=" + seatId);
                throw new SeatAlreadyReservedException("이미 예약된 좌석입니다.");
            }

            updateSeatStatus(scheduleId, seatId, "RESERVED");
            System.out.println("[TEST] 좌석 상태 업데이트 완료: scheduleId=" + scheduleId + ", seatId=" + seatId + ", 상태=RESERVED");

            return new SeatDto(seatId, "RESERVED");
        });
    }

    @Transactional
    public List<SeatDto> autoAssignSeats(int scheduleId, int numSeats) {
        // Step 1: Redis에서 사용 가능한 좌석 ID 가져오기
        List<Integer> availableSeatIds = redisRepository.getAvailableSeatIds(scheduleId);
        System.out.println("[DEBUG] Step 1: Redis에서 가져온 availableSeatIds=" + availableSeatIds);

        // Step 2: Redis에 데이터가 없으면 DB에서 좌석 상태 가져오기
        if (availableSeatIds.isEmpty()) {
            System.out.println("[DEBUG] Step 2: Redis에 데이터 없음. DB에서 좌석 상태 가져오기...");
            availableSeatIds = fetchAvailableSeats(scheduleId, numSeats);
            System.out.println("[DEBUG] Step 2: DB에서 가져온 availableSeatIds=" + availableSeatIds);
        }

        // Step 3: 연속된 좌석 찾기
        System.out.println("[DEBUG] Step 3: 연속된 좌석 찾기 시작...");
        List<Integer> assignedSeatIds = findConsecutiveSeats(availableSeatIds, numSeats);
        System.out.println("[DEBUG] Step 3: 연속된 좌석 찾기 결과 assignedSeatIds=" + assignedSeatIds);

        // Step 4: 연속된 좌석이 없으면 새로 생성 및 할당
        if (assignedSeatIds.isEmpty()) {
            System.out.println("[DEBUG] Step 4: 연속된 좌석 없음. 새 좌석 생성 및 할당...");
            assignedSeatIds = createAndAssignNewSeats(scheduleId, numSeats);
            System.out.println("[DEBUG] Step 4: 새로 생성된 assignedSeatIds=" + assignedSeatIds);
        }

        // Step 5: 좌석 상태 업데이트
        System.out.println("[DEBUG] Step 5: 상태 업데이트 시작... assignedSeatIds=" + assignedSeatIds);

        assignedSeatIds.forEach(seatId -> {
            System.out.println("[DEBUG] Step 5: 상태 업데이트 중... 현재 처리 중인 seatId=" + seatId);
            updateSeatStatus(scheduleId, seatId, "RESERVED");
        });

        System.out.println("[DEBUG] Step 5: 좌석 상태 업데이트 완료. 최종 assignedSeatIds=" + assignedSeatIds);

        // Step 6: SeatDto 리스트 반환
        return assignedSeatIds.stream()
                .map(seatId -> new SeatDto(seatId, "RESERVED"))
                .toList();
    }

    public List<SeatDto> getSeatsStatus(int scheduleId) {
        return seatReservationRepository.findByScheduleId(scheduleId)
                .stream()
                .map(res -> new SeatDto(res.getSeat().getSeatId(), res.getStatus()))
                .toList();
    }

    // -------------------------------
    // Private Helper Methods
    // -------------------------------

    private String fetchAndSyncSeatStatus(int scheduleId, int seatId) {
        System.out.println("[TEST] seatReservationRepository 호출: seatId=" + seatId + ", scheduleId=" + scheduleId);
        Optional<SeatReservation> dbReservation = seatReservationRepository.findBySeatAndSchedule(seatId, scheduleId);
        System.out.println("[TEST] seatReservationRepository 결과: " + dbReservation);

        if (dbReservation.isEmpty()) {
            Seat seat = seatRepository.findById(seatId)
                    .orElseThrow(() -> new RuntimeException("좌석 정보를 찾을 수 없습니다."));

            SeatReservation newReservation = new SeatReservation();
            newReservation.setSeat(seat);
            newReservation.setScheduleId(scheduleId);
            newReservation.setStatus("AVAILABLE");
            seatReservationRepository.save(newReservation);

            redisRepository.setSeatStatus(generateSeatKey(scheduleId, seatId), "AVAILABLE", SEAT_RESERVATION_TTL);
            System.out.println("[TEST] DB 및 Redis에 새로운 상태 추가: seatId=" + seatId + ", scheduleId=" + scheduleId + ", 상태=AVAILABLE");
            return "AVAILABLE";
        } else {
            String status = dbReservation.get().getStatus();
            redisRepository.setSeatStatus(generateSeatKey(scheduleId, seatId), status, SEAT_RESERVATION_TTL);
            System.out.println("[TEST] 기존 DB 상태를 Redis에 동기화: seatId=" + seatId + ", scheduleId=" + scheduleId + ", 상태=" + status);
            return status;
        }
    }

    private List<Integer> fetchAvailableSeats(int scheduleId, int numSeats) {
        System.out.println("[DEBUG] fetchAvailableSeats 호출: scheduleId=" + scheduleId + ", numSeats=" + numSeats);

        // Step 1: DB에서 사용 가능한 좌석 ID 가져오기
        List<Integer> availableSeatIds = seatReservationRepository.findAvailableSeats(scheduleId)
                .stream()
                .map(res -> res.getSeat().getSeatId())
                .toList();
        System.out.println("[DEBUG] fetchAvailableSeats 결과: availableSeatIds=" + availableSeatIds);

        // Step 2: 사용 가능한 좌석이 부족한 경우 필요한 만큼 생성
        if (availableSeatIds.isEmpty()) {
            System.out.println("[DEBUG] 사용 가능한 좌석이 없음. 새 좌석 생성 시작...");
            availableSeatIds = createAndAssignNewSeats(scheduleId, numSeats);
            System.out.println("[DEBUG] 새로 생성된 좌석 ID: " + availableSeatIds);
        }

        return availableSeatIds;
    }


    private List<Integer> createAndAssignNewSeats(int scheduleId, int numSeats) {
        System.out.println("[DEBUG] createAndAssignNewSeats 호출: scheduleId=" + scheduleId + ", numSeats=" + numSeats);

        try {
            List<String> allSeatRows = seatRepository.findAllSeatRows();
            System.out.println("[DEBUG] 전체 좌석 행 정보: allSeatRows=" + allSeatRows);

            for (String seatRow : allSeatRows) {
                List<Integer> seatIdsInRow = seatRepository.findSeatIdsByRow(seatRow);
                System.out.println("[DEBUG] 현재 열의 좌석 ID: seatRow=" + seatRow + ", seatIdsInRow=" + seatIdsInRow);

                List<Integer> reservedSeatIds = seatReservationRepository.findByScheduleIdAndSeatRow(scheduleId, seatRow)
                        .stream()
                        .map(reservation -> reservation.getSeat().getSeatId())
                        .toList();
                System.out.println("[DEBUG] 현재 열의 예약된 좌석 ID: reservedSeatIds=" + reservedSeatIds);

                List<Integer> availableSeatIds = seatIdsInRow.stream()
                        .filter(seatId -> !reservedSeatIds.contains(seatId))
                        .toList();
                System.out.println("[DEBUG] 현재 열의 사용 가능한 좌석 ID: availableSeatIds=" + availableSeatIds);

                List<Integer> assignedSeats = findConsecutiveSeats(availableSeatIds, numSeats);
                if (!assignedSeats.isEmpty()) {
                    System.out.println("[DEBUG] 연속된 좌석 찾기 성공: assignedSeats=" + assignedSeats);
                    return assignedSeats;
                }
            }
        } catch (Exception e) {
            System.out.println("[ERROR] createAndAssignNewSeats 실행 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }

        throw new SeatsNotAvailableException("요청한 좌석 수를 자동 배정할 수 없습니다.");
    }

    public boolean updateSeatStatus(int scheduleId, int seatId, String status) {
        System.out.println("[DEBUG] Seat 상태 업데이트 시작: scheduleId=" + scheduleId + ", seatId=" + seatId);

        // 존재 여부 확인
        SeatReservation reservation = seatReservationRepository.findBySeatAndSchedule(seatId, scheduleId)
                .orElseThrow(() -> new RuntimeException("좌석 정보를 찾을 수 없습니다: seatId=" + seatId + ", scheduleId=" + scheduleId));

        // 상태 업데이트
        reservation.setStatus(status);
        seatReservationRepository.save(reservation);
        System.out.println("[DEBUG] Seat 상태 업데이트 완료: " + reservation);
        return true;
    }

    private boolean isSeatReserved(String seatKey) {
        return "RESERVED".equals(redisRepository.getSeatStatus(seatKey));
    }

    private String generateLockKey(int scheduleId, int seatId) {
        return String.format("seat:lock:%d:%d", scheduleId, seatId);
    }

    private String generateSeatKey(int scheduleId, int seatId) {
        return String.format("seat:%d:%d", scheduleId, seatId);
    }

    private List<Integer> findConsecutiveSeats(List<Integer> availableSeatIds, int numSeats) {
        System.out.println("[DEBUG] findConsecutiveSeats 호출: availableSeatIds=" + availableSeatIds + ", numSeats=" + numSeats);

        if (availableSeatIds == null || availableSeatIds.isEmpty()) {
            System.out.println("[DEBUG] 사용 가능한 좌석 ID가 비어 있습니다. 빈 리스트 반환.");
            return Collections.emptyList();
        }

        // 리스트 복사본 생성
        List<Integer> mutableAvailableSeatIds = new ArrayList<>(availableSeatIds);

        try {
            // 정렬
            Collections.sort(mutableAvailableSeatIds);
            System.out.println("[DEBUG] 정렬된 availableSeatIds: " + mutableAvailableSeatIds);

            List<Integer> consecutiveSeats = new ArrayList<>();
            for (int i = 0; i < mutableAvailableSeatIds.size(); i++) {
                if (consecutiveSeats.isEmpty() ||
                        mutableAvailableSeatIds.get(i) == consecutiveSeats.get(consecutiveSeats.size() - 1) + 1) {
                    consecutiveSeats.add(mutableAvailableSeatIds.get(i));
                    if (consecutiveSeats.size() == numSeats) {
                        System.out.println("[DEBUG] 연속된 좌석 찾기 성공: " + consecutiveSeats);
                        return consecutiveSeats;
                    }
                } else {
                    consecutiveSeats.clear();
                    consecutiveSeats.add(mutableAvailableSeatIds.get(i));
                }
            }
        } catch (Exception e) {
            System.out.println("[ERROR] findConsecutiveSeats 실행 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("[DEBUG] 연속된 좌석을 찾지 못함. 빈 리스트 반환.");
        return Collections.emptyList();
    }


}

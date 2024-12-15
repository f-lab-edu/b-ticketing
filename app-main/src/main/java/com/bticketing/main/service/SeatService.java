package com.bticketing.main.service;

import com.bticketing.main.dto.SeatDto;
import com.bticketing.main.entity.Seat;
import com.bticketing.main.entity.SeatReservation;
import com.bticketing.main.exception.SeatAllReservedException;
import com.bticketing.main.exception.SeatAlreadyReservedException;
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
        logger.debug("[DEBUG] Redis 상태 확인: seatKey={}, redisStatus={}", seatKey, redisStatus);

        // DB 동기화
        if (redisStatus == null) {
            redisStatus = fetchAndSyncSeatStatus(scheduleId, seatId);
            logger.debug("[DEBUG] DB 동기화 후 Redis 상태: seatKey={}, redisStatus={}", seatKey, redisStatus);
        }

        if ("RESERVED".equals(redisStatus)) {
            logger.debug("[DEBUG] 좌석이 이미 예약됨: seatKey={}, scheduleId={}, seatId={}", seatKey, scheduleId, seatId);
            throw new SeatAlreadyReservedException("이미 예약된 좌석입니다.");
        }

        return redisRepository.executeWithLock(lockKey, SEAT_RESERVATION_TTL, () -> {
            logger.debug("[DEBUG] 락 획득 후 작업 실행: lockKey={}", lockKey);

            String currentStatus = redisRepository.getSeatStatus(seatKey);
            logger.debug("[DEBUG] 락 획득 후 Redis 상태 확인: seatKey={}, currentStatus={}", seatKey, currentStatus);

            if ("RESERVED".equals(currentStatus)) {
                logger.debug("[DEBUG] 좌석이 이미 예약됨: seatKey={}, scheduleId={}, seatId={}", seatKey, scheduleId, seatId);
                throw new SeatAlreadyReservedException("이미 예약된 좌석입니다.");
            }

            // Seat 객체 조회
            Seat seat = seatRepository.findById(seatId)
                    .orElseThrow(() -> new RuntimeException("좌석 정보를 찾을 수 없습니다. seatId=" + seatId));

            // updateSeatStatuses 호출
            updateSeatStatuses(scheduleId, List.of(seat), "RESERVED");
            logger.debug("[DEBUG] 좌석 상태 업데이트 완료: scheduleId={}, seatId={}, 상태=RESERVED", scheduleId, seatId);

            return new SeatDto(seatId, "RESERVED");
        });
    }

    @Transactional
    public List<SeatDto> autoAssignSeats(int scheduleId, int numSeats) {
        logger.debug("[DEBUG] autoAssignSeats 시작. scheduleId={}, 요청 좌석 수={}", scheduleId, numSeats);

        // Step 1: Redis에서 가능한 좌석 확인
        List<Integer> redisSeatIds = fetchAvailableSeatsFromRedis(scheduleId, numSeats);
        List<Seat> redisSeats = convertSeatIdsToSeats(redisSeatIds);

        List<Seat> assignedSeats = findConsecutiveSeatsInSameRow(redisSeats, numSeats);
        if (assignedSeats.size() == numSeats) {
            logger.debug("[DEBUG] Redis에서 요청 좌석 확보 완료: {}", assignedSeats);
            updateSeatStatuses(scheduleId, assignedSeats, "RESERVED");
            return convertToSeatDtos(assignedSeats);
        }

        // Step 2: Redis에서 부족할 경우 DB에서 추가 확인
        List<Seat> dbSeats = fetchAvailableSeatsFromDB(scheduleId);
        assignedSeats = findConsecutiveSeatsInSameRow(dbSeats, numSeats);

        if (assignedSeats.size() == numSeats) {
            logger.debug("[DEBUG] DB에서 요청 좌석 확보 완료: {}", assignedSeats);
            updateSeatStatuses(scheduleId, assignedSeats, "RESERVED");
            return convertToSeatDtos(assignedSeats);
        }

        // Step 3: Seat 테이블에서 좌석 확보 및 예약 생성
        List<Seat> newSeats = createNewSeatReservations(scheduleId, numSeats);
        if (newSeats.size() < numSeats) {
            throw new SeatAllReservedException("요청한 좌석 수를 자동 배정할 수 없습니다.");
        }

        logger.debug("[DEBUG] 새 좌석 예약 완료: {}", newSeats);
        updateSeatStatuses(scheduleId, newSeats, "RESERVED");
        return convertToSeatDtos(newSeats);
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

    // 분산락 걸기 메서드
    private String generateLockKey(int scheduleId, int seatId) {
        return String.format("seat:lock:%d:%d", scheduleId, seatId);
    }

    // redis 좌석 키 생성 메서드
    private String generateSeatKey(int scheduleId, int seatId) {
        return String.format("seat:%d:%d", scheduleId, seatId);
    }

    private List<SeatDto> convertToSeatDtos(List<Seat> seats) {
        return seats.stream()
                .map(seat -> new SeatDto(seat.getSeatId(), "RESERVED")) // 상태를 RESERVED로 설정
                .toList();
    }
    private List<Seat> convertSeatIdsToSeats(List<Integer> seatIds) {
        return seatRepository.findAllById(seatIds);
    }

    //Redis 좌석 상태 조회 후 db상태 조회 및 동기화 메서드
    private String fetchAndSyncSeatStatus(int scheduleId, int seatId) {
        Optional<SeatReservation> dbReservation = seatReservationRepository.findBySeatAndSchedule(seatId, scheduleId);
        if (dbReservation.isEmpty()) {
            Seat seat = seatRepository.findById(seatId)
                    .orElseThrow(() -> new RuntimeException("좌석 정보를 찾을 수 없습니다."));

            SeatReservation newReservation = new SeatReservation();
            newReservation.setSeat(seat);
            newReservation.setScheduleId(scheduleId);
            newReservation.setStatus("AVAILABLE");
            seatReservationRepository.save(newReservation);

            redisRepository.setSeatStatus(generateSeatKey(scheduleId, seatId), "AVAILABLE", SEAT_RESERVATION_TTL);
            return "AVAILABLE";
        } else {
            String status = dbReservation.get().getStatus();
            redisRepository.setSeatStatus(generateSeatKey(scheduleId, seatId), status, SEAT_RESERVATION_TTL);
            return status;
        }
    }

    //Redis AVAILABLE 조회 메서드
    private List<Integer> fetchAvailableSeatsFromRedis(int scheduleId, int numSeats) {
        logger.debug("[DEBUG] Redis에서 AVAILABLE 상태 좌석 조회 시작. scheduleId={}, 요청 좌석 수={}", scheduleId, numSeats);

        String pattern = "seat:" + scheduleId + ":*";
        Set<String> keys = redisRepository.scanKeys(pattern);

        List<Integer> availableSeats = new ArrayList<>();
        for (String key : keys) {
            String status = redisRepository.getSeatStatus(key);
            if ("AVAILABLE".equals(status)) {
                int seatId = Integer.parseInt(key.split(":")[2]);
                availableSeats.add(seatId);
            }
            if (availableSeats.size() >= numSeats) {
                break;
            }
        }

        logger.debug("[DEBUG] Redis에서 조회된 AVAILABLE 좌석: {}", availableSeats);
        return availableSeats;
    }

    //DB AVAILABLE 조회 메서드
    private List<Seat> fetchAvailableSeatsFromDB(int scheduleId) {
        logger.debug("[DEBUG] DB에서 사용 가능한 좌석 조회 시작. scheduleId={}", scheduleId);

        List<SeatReservation> reservations = seatReservationRepository.findAvailableSeats(scheduleId);
        return reservations.stream()
                .map(SeatReservation::getSeat)
                .collect(Collectors.toList());
    }

    //new SeatReservation값 생성 메서드
    private List<Seat> createNewSeatReservations(int scheduleId, int numSeats) {
        logger.debug("[DEBUG] Seat 테이블에서 새 좌석 예약 생성 시작. scheduleId={}, 요청 좌석 수={}", scheduleId, numSeats);

        List<Seat> allSeats = seatRepository.findAll(); // 모든 좌석 조회
        Set<Integer> reservedSeatIds = seatReservationRepository.findByScheduleId(scheduleId)
                .stream()
                .map(res -> res.getSeat().getSeatId())
                .collect(Collectors.toSet());

        List<Seat> availableSeats = allSeats.stream()
                .filter(seat -> !reservedSeatIds.contains(seat.getSeatId()))
                .toList();

        List<Seat> newReservations = new ArrayList<>();
        for (int i = 0; i < Math.min(numSeats, availableSeats.size()); i++) {
            Seat seat = availableSeats.get(i);
            SeatReservation reservation = new SeatReservation(seat, scheduleId, "RESERVED");
            seatReservationRepository.save(reservation);

            String seatKey = generateSeatKey(scheduleId, seat.getSeatId());
            redisRepository.setSeatStatus(seatKey, "RESERVED", SEAT_RESERVATION_TTL);

            newReservations.add(seat);
        }

        return newReservations;
    }

    //연속좌석 탐색 메서드
    private List<Seat> findConsecutiveSeatsInSameRow(List<Seat> seats, int numSeats) {
        Map<String, List<Seat>> seatsByRow = seats.stream()
                .collect(Collectors.groupingBy(Seat::getSeatRow));

        for (List<Seat> rowSeats : seatsByRow.values()) {
            rowSeats.sort(Comparator.comparingInt(Seat::getSeatNumber));

            List<Seat> consecutiveSeats = new ArrayList<>();
            for (Seat seat : rowSeats) {
                if (consecutiveSeats.isEmpty() ||
                        consecutiveSeats.get(consecutiveSeats.size() - 1).getSeatNumber() + 1 == seat.getSeatNumber()) {
                    consecutiveSeats.add(seat);
                    if (consecutiveSeats.size() == numSeats) {
                        return consecutiveSeats;
                    }
                } else {
                    consecutiveSeats.clear();
                    consecutiveSeats.add(seat);
                }
            }
        }
        return Collections.emptyList();
    }

    //Redis,DB 동기화 및 상테 업데이트 메서드
    private void updateSeatStatuses(int scheduleId, List<Seat> seats, String status) {
        for (Seat seat : seats) {
            String redisKey = generateSeatKey(scheduleId, seat.getSeatId());
            redisRepository.setSeatStatus(redisKey, status, SEAT_RESERVATION_TTL);

            SeatReservation reservation = seatReservationRepository.findBySeatAndSchedule(seat.getSeatId(), scheduleId)
                    .orElseGet(() -> new SeatReservation(seat, scheduleId, status));

            reservation.setStatus(status);
            seatReservationRepository.save(reservation);
        }
    }



}

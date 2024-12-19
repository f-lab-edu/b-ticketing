package com.bticketing.main.service;

import com.bticketing.main.dto.SeatDto;
import com.bticketing.main.entity.Seat;
import com.bticketing.main.exception.SeatAllReservedException;
import com.bticketing.main.exception.SeatAlreadyReservedException;
import com.bticketing.main.repository.redis.SeatRedisRepository;
import com.bticketing.main.repository.seat.SeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
public class SeatService {

    private static final Logger logger = LoggerFactory.getLogger(SeatService.class);

    private final SeatRedisRepository redisRepository;
    private final SeatRepository seatRepository;
    private final SeatTransactionManager transactionManager;
    private final Executor threadPoolTaskExecutor;

    private static final int SEAT_RESERVATION_TTL = 300; // 예약 TTL (초)

    public SeatService(SeatRedisRepository redisRepository,
                       SeatRepository seatRepository,
                       SeatTransactionManager transactionManager,
                       @Qualifier("threadPoolTaskExecutor") Executor threadPoolTaskExecutor) {
        this.redisRepository = redisRepository;
        this.seatRepository = seatRepository;
        this.transactionManager = transactionManager;
        this.threadPoolTaskExecutor = threadPoolTaskExecutor;
    }

    @Async("threadPoolTaskExecutor")
    public CompletableFuture<SeatDto> selectSeat(int scheduleId, int seatId) {
        String lockKey = generateLockKey(scheduleId, seatId);
        String seatKey = generateSeatKey(scheduleId, seatId);

        return CompletableFuture.completedFuture(redisRepository.getSeatStatus(seatKey))
                .thenCompose(redisStatus -> {
                    if (redisStatus == null) {
                        return CompletableFuture.supplyAsync(() ->
                                transactionManager.fetchAndSyncSeatStatus(scheduleId, seatId), threadPoolTaskExecutor);
                    } else {
                        return CompletableFuture.completedFuture(redisStatus);
                    }
                })
                .thenCompose(status -> {
                    if ("RESERVED".equals(status)) {
                        return CompletableFuture.failedFuture(new SeatAlreadyReservedException("이미 예약된 좌석입니다."));
                    } else {
                        return redisRepository.executeWithLockAsync(lockKey, SEAT_RESERVATION_TTL, () ->
                                CompletableFuture.supplyAsync(() -> {
                                    String currentStatus = redisRepository.getSeatStatus(seatKey);
                                    if ("RESERVED".equals(currentStatus)) {
                                        throw new SeatAlreadyReservedException("이미 예약된 좌석입니다.");
                                    }
                                    Seat seat = seatRepository.findById(seatId)
                                            .orElseThrow(() -> new RuntimeException("좌석 정보를 찾을 수 없습니다. seatId=" + seatId));

                                    transactionManager.updateSeatStatuses(scheduleId, List.of(seat), "RESERVED");
                                    logger.debug("[DEBUG] 좌석 상태 업데이트 완료: scheduleId={}, seatId={}, 상태=RESERVED", scheduleId, seatId);
                                    return new SeatDto(seat.getSeatId(), "RESERVED");
                                }, threadPoolTaskExecutor));
                    }
                })
                .handle((result, ex) -> {
                    if (ex != null) {
                        logger.error("좌석 선택 중 에러 발생: ", ex);
                        if (ex.getCause() instanceof SeatAlreadyReservedException) {
                            throw (SeatAlreadyReservedException) ex.getCause();
                        }
                        throw new RuntimeException("좌석 선택 중 에러가 발생했습니다.", ex.getCause());
                    }
                    return result;
                });
    }

    @Async("threadPoolTaskExecutor")
    public CompletableFuture<List<SeatDto>> autoAssignSeats(int scheduleId, int numSeats) {
        logger.debug("[DEBUG] autoAssignSeats 시작. scheduleId={}, 요청 좌석 수={}", scheduleId, numSeats);

        return CompletableFuture.supplyAsync(() -> {
                    List<Integer> redisSeatIds = fetchAvailableSeatsFromRedis(scheduleId, numSeats);
                    List<Seat> redisSeats = convertSeatIdsToSeats(redisSeatIds);

                    List<Seat> assignedSeats = findConsecutiveSeatsInSameRow(redisSeats, numSeats);
                    if (assignedSeats.size() == numSeats) {
                        logger.debug("[DEBUG] Redis에서 요청 좌석 확보 완료: {}", assignedSeats);
                        transactionManager.updateSeatStatuses(scheduleId, assignedSeats, "RESERVED");
                        return convertToSeatDtos(assignedSeats);
                    }

                    return transactionManager.findAndAssignAvailableSeats(scheduleId, numSeats);
                }, threadPoolTaskExecutor)
                .handle((result, ex) -> {
                    if (ex != null) {
                        logger.error("좌석 자동 할당 중 에러 발생: ", ex);
                        if (ex.getCause() instanceof SeatAllReservedException) {
                            throw (SeatAllReservedException) ex.getCause(); // SeatAllReservedException을 그대로 throw
                        }
                        throw new RuntimeException("좌석 자동 할당 중 에러가 발생했습니다.", ex.getCause());
                    }
                    return result;
                });
    }

    public CompletableFuture<List<SeatDto>> getSeatsStatus(int scheduleId) {
        return CompletableFuture.supplyAsync(() -> transactionManager.getSeatsStatus(scheduleId), threadPoolTaskExecutor);
    }

    // -------------------------------
    // Private Helper Methods
    // -------------------------------

    private String generateLockKey(int scheduleId, int seatId) {
        return String.format("seat:lock:%d:%d", scheduleId, seatId);
    }

    private String generateSeatKey(int scheduleId, int seatId) {
        return String.format("seat:%d:%d", scheduleId, seatId);
    }

    private List<SeatDto> convertToSeatDtos(List<Seat> seats) {
        return seats.stream()
                .map(seat -> new SeatDto(seat.getSeatId(), "RESERVED"))
                .toList();
    }

    private List<Seat> convertSeatIdsToSeats(List<Integer> seatIds) {
        return seatRepository.findAllById(seatIds);
    }

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
}

package com.bticketing.main.service;

import com.bticketing.main.dto.SeatDto;
import com.bticketing.main.entity.Seat;
import com.bticketing.main.entity.SeatReservation;
import com.bticketing.main.exception.SeatAllReservedException;
import com.bticketing.main.repository.redis.SeatRedisRepository;
import com.bticketing.main.repository.seat.SeatRepository;
import com.bticketing.main.repository.seat.SeatReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SeatTransactionManager {

    private static final Logger logger = LoggerFactory.getLogger(SeatTransactionManager.class);
    private final SeatRepository seatRepository;
    private final SeatReservationRepository seatReservationRepository;
    private final SeatRedisRepository redisRepository;
    private static final int SEAT_RESERVATION_TTL = 300; // 예약 TTL (초)

    public SeatTransactionManager(SeatRepository seatRepository,
                                  SeatReservationRepository seatReservationRepository,
                                  SeatRedisRepository redisRepository) {
        this.seatRepository = seatRepository;
        this.seatReservationRepository = seatReservationRepository;
        this.redisRepository = redisRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateSeatStatuses(int scheduleId, List<Seat> seats, String status) {
        for (Seat seat : seats) {
            String redisKey = String.format("seat:%d:%d", scheduleId, seat.getSeatId());
            redisRepository.setSeatStatus(redisKey, status, SEAT_RESERVATION_TTL);

            SeatReservation reservation = seatReservationRepository.findBySeatAndSchedule(seat.getSeatId(), scheduleId)
                    .orElseGet(() -> {
                        SeatReservation newReservation = new SeatReservation(seat, scheduleId, status);
                        return seatReservationRepository.save(newReservation);
                    });

            reservation.setStatus(status);
            seatReservationRepository.save(reservation);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String fetchAndSyncSeatStatus(int scheduleId, int seatId) {
        Optional<SeatReservation> dbReservation = seatReservationRepository.findBySeatAndSchedule(seatId, scheduleId);
        String seatKey = String.format("seat:%d:%d", scheduleId, seatId);
        if (dbReservation.isEmpty()) {
            Seat seat = seatRepository.findById(seatId)
                    .orElseThrow(() -> new RuntimeException("좌석 정보를 찾을 수 없습니다."));

            SeatReservation newReservation = new SeatReservation();
            newReservation.setSeat(seat);
            newReservation.setScheduleId(scheduleId);
            newReservation.setStatus("AVAILABLE");
            seatReservationRepository.save(newReservation);

            redisRepository.setSeatStatus(seatKey, "AVAILABLE", SEAT_RESERVATION_TTL);
            return "AVAILABLE";
        } else {
            String status = dbReservation.get().getStatus();
            redisRepository.setSeatStatus(seatKey, status, SEAT_RESERVATION_TTL);
            return status;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Seat> createNewSeatReservations(int scheduleId, int numSeats) {
        logger.debug("[DEBUG] Seat 테이블에서 새 좌석 예약 생성 시작. scheduleId={}, 요청 좌석 수={}", scheduleId, numSeats);

        List<Seat> allSeats = seatRepository.findAll();
        Set<Integer> reservedSeatIds = seatReservationRepository.findByScheduleId(scheduleId)
                .stream()
                .map(res -> res.getSeat().getSeatId())
                .collect(Collectors.toSet());

        List<Seat> availableSeats = allSeats.stream()
                .filter(seat -> !reservedSeatIds.contains(seat.getSeatId()))
                .toList();
        if (availableSeats.isEmpty()) {
            throw new SeatAllReservedException("요청한 좌석 수를 자동 배정할 수 없습니다.");
        }
        List<Seat> newReservations = new ArrayList<>();
        for (int i = 0; i < Math.min(numSeats, availableSeats.size()); i++) {
            Seat seat = availableSeats.get(i);
            SeatReservation reservation = new SeatReservation(seat, scheduleId, "RESERVED");
            seatReservationRepository.save(reservation);

            String seatKey = String.format("seat:%d:%d", scheduleId, seat.getSeatId());
            redisRepository.setSeatStatus(seatKey, "RESERVED", SEAT_RESERVATION_TTL);

            newReservations.add(seat);
        }

        return newReservations;
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<SeatDto> findAndAssignAvailableSeats(int scheduleId, int numSeats) {
        List<Seat> dbSeats = fetchAvailableSeatsFromDB(scheduleId);
        List<Seat> assignedSeats = findConsecutiveSeatsInSameRow(dbSeats, numSeats);

        if (assignedSeats.size() == numSeats) {
            logger.debug("[DEBUG] DB에서 요청 좌석 확보 완료: {}", assignedSeats);
            updateSeatStatuses(scheduleId, assignedSeats, "RESERVED");
            return convertToSeatDtos(assignedSeats);
        }

        List<Seat> newSeats = createNewSeatReservations(scheduleId, numSeats);
        if (newSeats.isEmpty() || newSeats.size() < numSeats) { // 좌석이 부족한 경우 예외 발생
            throw new SeatAllReservedException("요청한 좌석 수를 자동 배정할 수 없습니다.");
        }

        logger.debug("[DEBUG] 새 좌석 예약 완료: {}", newSeats);
        updateSeatStatuses(scheduleId, newSeats, "RESERVED");
        return convertToSeatDtos(newSeats);
    }

    private List<Seat> fetchAvailableSeatsFromDB(int scheduleId) {
        logger.debug("[DEBUG] DB에서 사용 가능한 좌석 조회 시작. scheduleId={}", scheduleId);

        List<SeatReservation> reservations = seatReservationRepository.findAvailableSeats(scheduleId);
        return reservations.stream()
                .map(SeatReservation::getSeat)
                .collect(Collectors.toList());
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
    private List<SeatDto> convertToSeatDtos(List<Seat> seats) {
        return seats.stream()
                .map(seat -> new SeatDto(seat.getSeatId(), "RESERVED"))
                .toList();
    }

    public List<SeatDto> getSeatsStatus(int scheduleId) {
        return seatReservationRepository.findByScheduleId(scheduleId)
                .stream()
                .map(res -> new SeatDto(res.getSeat().getSeatId(), res.getStatus()))
                .toList();
    }
}

package com.bticketing.main.repository.seat;

import com.bticketing.main.entity.Seat;
import com.bticketing.main.entity.SeatReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


@Repository
public interface SeatReservationRepository extends JpaRepository<SeatReservation, Integer> {
    // 일정 ID를 기반으로 모든 좌석 예약 정보 가져오기
    List<SeatReservation> findByScheduleId(int scheduleId);

    @Query("SELECT r FROM SeatReservation r WHERE r.seat.seatId = :seatId AND r.scheduleId = :scheduleId")
    Optional<SeatReservation> findBySeatAndSchedule(@Param("seatId") int seatId, @Param("scheduleId") int scheduleId);

    @Query("SELECT r FROM SeatReservation r JOIN FETCH r.seat WHERE r.scheduleId = :scheduleId AND r.status = 'AVAILABLE'")
    List<SeatReservation> findAvailableSeats(@Param("scheduleId") int scheduleId);

}

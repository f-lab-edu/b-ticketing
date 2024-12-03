package com.bticketing.main.repository.seat;

import com.bticketing.main.entity.SeatReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeatReservationRepository extends JpaRepository<SeatReservation, Integer> {
    // 일정 ID를 기반으로 모든 좌석 예약 정보 가져오기
    List<SeatReservation> findByScheduleId(int scheduleId);

    // 일정 ID와 상태로 필터링된 좌석 예약 정보 가져오기
    List<SeatReservation> findByScheduleIdAndStatus(int scheduleId, String status);

}

package com.bticketing.main.repository;

import com.bticketing.main.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Integer> {
    // 필요한 경우 커스텀 메서드 추가 가능
    List<Seat> findByScheduleIdAndSectionId(int scheduleId, int sectionId);
}

package com.bticketing.main.repository.seat;

import com.bticketing.main.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Integer> {

    List<Seat> findByScheduleIdAndSectionId(int scheduleId, int sectionId);
}

package com.bticketing.main.repository.seat;

import com.bticketing.main.entity.Seat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Integer> {
    @Query("SELECT s.seatId FROM Seat s")
    List<Integer> findAllSeatIds();
}

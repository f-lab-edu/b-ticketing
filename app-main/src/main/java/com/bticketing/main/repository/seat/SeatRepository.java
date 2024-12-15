package com.bticketing.main.repository.seat;

import com.bticketing.main.entity.Seat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Integer> {
    @Query("SELECT DISTINCT s.seatRow FROM Seat s")
    List<String> findAllSeatRows();

    @Query("SELECT s.seatId FROM Seat s WHERE s.seatRow = :seatRow ORDER BY s.seatNumber")
    List<Integer> findSeatIdsByRow(@Param("seatRow") String seatRow);

}

package com.bticketing.main.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "seat_reservation")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeatReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int reservationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Column(nullable = false)
    private int scheduleId; // 경기 또는 일정 ID

    @Column(nullable = false)
    private String status; // 예약 상태 (RESERVED, COMPLETED 등)

    public SeatReservation(Seat seat, int scheduleId, String status) {
        this.seat = seat;
        this.scheduleId = scheduleId;
        this.status = status;
    }
}

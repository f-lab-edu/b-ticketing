package com.bticketing.main.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.redis.core.RedisHash;

@Entity
@Table(name = "seat")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int seatId;

    @Column(nullable = false)
    private String SeatRow; // A, B, C 등 좌석 열

    @Column(nullable = false)
    private int SeatNumber; // 열 내 좌석 번호 (1, 2, 3...)


}

package com.bticketing.main.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.redis.core.RedisHash;

@Entity
@Table(name = "seats")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int seatId;

    @Column(nullable = false)
    private int scheduleId;

    @Column(nullable = false)
    private int sectionId;

    @Column(nullable = false)
    private String status; // 예: AVAILABLE, RESERVED

    public Seat(int scheduleId, int sectionId, String status) {
        this.scheduleId = scheduleId;
        this.sectionId = sectionId;
        this.status = status;
    }
}

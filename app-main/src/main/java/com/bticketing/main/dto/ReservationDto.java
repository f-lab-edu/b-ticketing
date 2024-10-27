package com.bticketing.main.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReservationDto {
    private int reservationId;
    private int scheduleId;
    private int sectionId;
    private int[] selectedSeats;

}

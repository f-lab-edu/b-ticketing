package com.bticketing.main.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReservationDto {
    private int reservationId;    // 예매 ID, 예매 내역을 고유 식별
    private int scheduleId;       // 예매한 경기 일정 ID
    private int sectionId;        // 예매한 좌석 구역 ID
    private int[] selectedSeats;  // 예매한 좌석 ID 리스트, 선택된 좌석 ID 목록

}

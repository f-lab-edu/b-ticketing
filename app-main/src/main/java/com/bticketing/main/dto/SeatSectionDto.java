package com.bticketing.main.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeatSectionDto {
    private int sectionId; //좌석 구역 ID, 좌석 구역을 식별
    private String sectionName; // 좌석 구역 이름,
}

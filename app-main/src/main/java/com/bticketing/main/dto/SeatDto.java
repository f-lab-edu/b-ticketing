package com.bticketing.main.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeatDto {

    private int sectionId;
    private String sectionName;
    private int seatId;
    private int row;
    private int number;
}

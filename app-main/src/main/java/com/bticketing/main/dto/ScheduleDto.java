package com.bticketing.main.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleDto {
    private int scheduleId;
    private String date;
    private String opponent;
    private String location;

}

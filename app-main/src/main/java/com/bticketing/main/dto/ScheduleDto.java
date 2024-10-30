package com.bticketing.main.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleDto {
    private int scheduleId;     // 경기 일정 ID, 특정 경기 식별
    private String date;        // 경기 날짜
    private String matchup;    // 경기하는 두 팀
    private String location;    // 경기 위치

}

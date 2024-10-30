package com.bticketing.main.controller;

import com.bticketing.main.dto.ScheduleDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;


@RestController
@RequestMapping("/schedules")
public class ScheduleController {

    //경기목록 api
    @GetMapping
    public List<ScheduleDto> getSchedules() {
        ScheduleDto schedule1 = new ScheduleDto(1, "2024-10-30", "한화 vs 두산 ", "대전");
        ScheduleDto schedule2 = new ScheduleDto(2, "2024-10-30", "기아 vs 삼성 ", "광주");
        return Arrays.asList(schedule1, schedule2);
    }
}

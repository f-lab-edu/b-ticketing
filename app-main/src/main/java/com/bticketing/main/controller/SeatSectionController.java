package com.bticketing.main.controller;

import com.bticketing.main.dto.SeatSectionDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/seats/sections")
public class SeatSectionController {

    // 좌석 구역 목록 api
    @GetMapping
    public List<SeatSectionDto> getSeatSections(@RequestParam int scheduleId) {

        SeatSectionDto section1 = new SeatSectionDto(1, "1st base");
        SeatSectionDto section2 = new SeatSectionDto(2, "3rd base");

        return Arrays.asList(section1, section2);
    }
}

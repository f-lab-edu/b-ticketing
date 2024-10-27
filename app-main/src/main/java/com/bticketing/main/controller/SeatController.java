package com.bticketing.main.controller;

import com.bticketing.main.dto.SeatDto;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;


@RestController
@RequestMapping("/seats")
public class SeatController {

    //좌석구역 목록 api
    @GetMapping("/sections")
    public List<SeatDto> getSeatSections(@RequestParam int scheduleId) {
        SeatDto section1 = new SeatDto(1, "1st base", 0, 0, 0);
        SeatDto section2 = new SeatDto(2, "3rd base", 0, 0, 0);
        return Arrays.asList(section1, section2);
    }

    //좌석선택 api
    @PostMapping("/select")
    public String selectSeats(@RequestBody List<Integer> selectedSeats) {
        return "선택한 좌석: " + selectedSeats;
    }
}

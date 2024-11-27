package com.bticketing.main.controller;

import com.bticketing.main.entity.Seat;
import com.bticketing.main.service.SeatService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/seats")
public class SeatSelectionController {

    private final SeatService seatService;

    public SeatSelectionController(SeatService seatService) {
        this.seatService = seatService;
    }

    // 수동 좌석 선택 API
    @PostMapping("/select")
    public String selectSeat(@RequestParam int seatId) {
        boolean isSelected = seatService.selectSeat(seatId);
        return isSelected ? "좌석이 성공적으로 선택되었습니다." : "좌석 선택에 실패했습니다.";
    }

    // 자동 좌석 선택 API
    @PostMapping("/auto-assign")
    public List<Seat> autoAssignSeats(
            @RequestParam int scheduleId,
            @RequestParam int sectionId,
            @RequestParam int numSeats) {
        return seatService.autoAssignSeats(scheduleId, sectionId, numSeats);
    }
}

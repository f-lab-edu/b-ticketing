package com.bticketing.main.controller;

import com.bticketing.main.dto.SeatDto;
import com.bticketing.main.service.SeatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/seats")
public class SeatSelectionController {

    private final SeatService seatService;

    public SeatSelectionController(SeatService seatService) {
        this.seatService = seatService;
    }

    @PostMapping("/select")
    public ResponseEntity<SeatDto> selectSeat(@RequestParam int scheduleId, @RequestParam int seatId) {
        SeatDto reservedSeat = seatService.selectSeat(scheduleId, seatId);
        return ResponseEntity.ok(reservedSeat);
    }

    @PostMapping("/auto-assign")
    public ResponseEntity<List<SeatDto>> autoAssignSeats(
            @RequestParam int scheduleId,
            @RequestParam int numSeats) {
        List<SeatDto> assignedSeats = seatService.autoAssignSeats(scheduleId, numSeats);
        return ResponseEntity.ok(assignedSeats);
    }

    @GetMapping("/status")
    public ResponseEntity<List<SeatDto>> getSeatsStatus(@RequestParam int scheduleId) {
        List<SeatDto> seatStatuses = seatService.getSeatsStatus(scheduleId);
        return ResponseEntity.ok(seatStatuses);
    }
}

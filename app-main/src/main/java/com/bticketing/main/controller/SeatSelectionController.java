package com.bticketing.main.controller;

import com.bticketing.main.dto.SeatDto;
import com.bticketing.main.service.SeatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/seats")
public class SeatSelectionController {

    private final SeatService seatService;

    public SeatSelectionController(SeatService seatService) {
        this.seatService = seatService;
    }

    @PostMapping("/select")
    public CompletableFuture<ResponseEntity<SeatDto>> selectSeat(@RequestParam int scheduleId, @RequestParam int seatId) {
        // 단순히 서비스 호출 결과를 반환
        return seatService.selectSeat(scheduleId, seatId)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/auto-assign")
    public CompletableFuture<ResponseEntity<List<SeatDto>>> autoAssignSeats(
            @RequestParam int scheduleId,
            @RequestParam int numSeats) {
        // 단순히 서비스 호출 결과를 반환
        return seatService.autoAssignSeats(scheduleId, numSeats)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/status")
    public CompletableFuture<ResponseEntity<List<SeatDto>>> getSeatsStatus(@RequestParam int scheduleId) {
        // 단순히 서비스 호출 결과를 반환
        return seatService.getSeatsStatus(scheduleId)
                .thenApply(ResponseEntity::ok);
    }
}

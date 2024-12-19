package com.bticketing.main.controller;

import com.bticketing.main.dto.SeatDto;
import com.bticketing.main.exception.SeatAllReservedException;
import com.bticketing.main.service.SeatService;
import org.springframework.http.HttpStatus;
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
        return seatService.selectSeat(scheduleId, seatId)
                .handle((seatDto, ex) -> {
                    if (ex != null) {
                        return ResponseEntity.status(HttpStatus.CONFLICT).body(new SeatDto(-1, ex.getCause().getMessage()));
                    } else {
                        return ResponseEntity.ok(seatDto);
                    }
                });
    }

    @PostMapping("/auto-assign")
    public CompletableFuture<ResponseEntity<List<SeatDto>>> autoAssignSeats(
            @RequestParam int scheduleId,
            @RequestParam int numSeats) {
        return seatService.autoAssignSeats(scheduleId, numSeats)
                .handle((seatDtos, ex) -> {
                    if (ex != null) {
                        if (ex instanceof SeatAllReservedException) { // SeatAllReservedException을 catch
                            return ResponseEntity.status(HttpStatus.CONFLICT).body(List.of()); // 409 Conflict 에러 반환
                        } else {
                            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of());
                        }
                    } else {
                        return ResponseEntity.ok(seatDtos);
                    }
                });
    }

    @GetMapping("/status")
    public CompletableFuture<ResponseEntity<List<SeatDto>>> getSeatsStatus(@RequestParam int scheduleId) {
        return seatService.getSeatsStatus(scheduleId)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of()));
    }
}

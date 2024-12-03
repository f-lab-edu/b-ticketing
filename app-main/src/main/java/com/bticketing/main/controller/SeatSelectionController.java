package com.bticketing.main.controller;

import com.bticketing.main.dto.SeatDto;
import com.bticketing.main.service.SeatService;
import org.springframework.http.HttpStatus;
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
        try {
            // `scheduleId`와 `seatId`를 기반으로 좌석 예약 처리
            SeatDto reservedSeat = seatService.selectSeat(scheduleId, seatId);
            return ResponseEntity.ok(reservedSeat);
        } catch (RuntimeException ex) {
            // 에러 메시지와 함께 응답 반환
            return ResponseEntity.badRequest().body(new SeatDto(seatId, ex.getMessage()));
        }
    }

    @PostMapping("/auto-assign")
    public ResponseEntity<?> autoAssignSeats(
            @RequestParam int scheduleId,
            @RequestParam int numSeats) {
        try {
            // SeatService에서 자동 좌석 배정 호출
            List<SeatDto> assignedSeats = seatService.autoAssignSeats(scheduleId, numSeats);
            return ResponseEntity.ok(assignedSeats);
        } catch (RuntimeException ex) {
            // 요청 실패 시 적절한 메시지 반환
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
    }

    @GetMapping("/status")
    public ResponseEntity<List<SeatDto>> getSeatsStatus(@RequestParam int scheduleId) {
        List<SeatDto> seatStatuses = seatService.getSeatsStatus(scheduleId);
        return ResponseEntity.ok(seatStatuses);
    }
}

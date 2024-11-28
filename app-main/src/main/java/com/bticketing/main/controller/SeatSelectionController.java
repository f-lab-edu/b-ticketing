package com.bticketing.main.controller;

import com.bticketing.main.dto.SeatDto;
import com.bticketing.main.messaging.WebSocketNotifier;
import com.bticketing.main.service.SeatService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/seats")
public class SeatSelectionController {

    private final SeatService seatService;
    private final WebSocketNotifier webSocketNotifier;

    public SeatSelectionController(SeatService seatService, WebSocketNotifier webSocketNotifier) {
        this.seatService = seatService;
        this.webSocketNotifier = webSocketNotifier;
    }

    @PostMapping("/select")
    public ResponseEntity<String> selectSeat(@RequestParam int seatId) {
        try {
            SeatDto selectedSeat = seatService.selectSeat(seatId);

            // WebSocket 알림 전송
            webSocketNotifier.notifySeatStatus(seatId, selectedSeat.getStatus());

            return ResponseEntity.ok("좌석이 성공적으로 선택되었습니다.");
        } catch (RuntimeException ex) {
            // 실패 상황에 대한 명확한 상태 코드와 메시지 반환
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
    }

    @PostMapping("/auto-assign")
    public ResponseEntity<?> autoAssignSeats(
            @RequestParam int scheduleId,
            @RequestParam int sectionId,
            @RequestParam int numSeats) {
        try {
            List<SeatDto> assignedSeats = seatService.autoAssignSeats(scheduleId, sectionId, numSeats);

            // WebSocket 알림 전송
            for (SeatDto seat : assignedSeats) {
                webSocketNotifier.notifySeatStatus(seat.getSeatId(), seat.getStatus());
            }

            return ResponseEntity.ok(assignedSeats); // 성공 시 200 OK와 JSON 반환
        } catch (RuntimeException ex) {
            // 실패 시 400 Bad Request와 에러 메시지 반환
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
    }
}

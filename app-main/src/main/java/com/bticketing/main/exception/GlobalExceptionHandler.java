package com.bticketing.main.exception;

import com.bticketing.main.dto.SeatDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    // 좌석이 이미 예약된 경우 처리
    @ExceptionHandler(SeatAlreadyReservedException.class)
    public ResponseEntity<SeatDto> handleSeatAlreadyReservedException(SeatAlreadyReservedException ex) {
        SeatDto errorResponse = new SeatDto(-1, ex.getMessage()); // -1은 seatId를 알 수 없음을 의미
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse); // 409 Conflict
    }

    // 자동좌석이 가능하지 않을 경우 처리
    @ExceptionHandler(SeatAllReservedException.class)
    public ResponseEntity<String> handleSeatAllReservedException(SeatAllReservedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    // 요청한 좌석을 찾을 수 없는 경우 처리
    @ExceptionHandler(SeatsNotAvailableException.class)
    public ResponseEntity<String> handleSeatsNotAvailableException(SeatsNotAvailableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage()); // 400 Bad Request
    }

    // 기타 RuntimeException 처리
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleRuntimeException(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("서버에서 오류가 발생했습니다: " + ex.getMessage());
    }
}

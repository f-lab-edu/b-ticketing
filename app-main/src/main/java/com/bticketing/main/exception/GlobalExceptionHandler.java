package com.bticketing.main.exception;

import com.bticketing.main.dto.SeatDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.concurrent.CompletionException;

@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 비동기 작업 중 발생하는 CompletionException 처리
     */
    @ExceptionHandler(CompletionException.class)
    public ResponseEntity<Object> handleCompletionException(CompletionException ex) {
        Throwable cause = ex.getCause(); // CompletionException 내부의 원인 예외를 가져옴

        if (cause instanceof SeatAlreadyReservedException) {
            return handleSeatAlreadyReservedException((SeatAlreadyReservedException) cause);
        } else if (cause instanceof SeatAllReservedException) {
            return handleSeatAllReservedException((SeatAllReservedException) cause);
        } else if (cause instanceof SeatsNotAvailableException) {
            return handleSeatsNotAvailableException((SeatsNotAvailableException) cause);
        }

        // 알 수 없는 예외 기본 처리
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("비동기 작업 중 알 수 없는 오류가 발생했습니다: " + cause.getMessage());
    }

    /**
     * 좌석이 이미 예약된 경우 처리
     */
    @ExceptionHandler(SeatAlreadyReservedException.class)
    public ResponseEntity<Object> handleSeatAlreadyReservedException(SeatAlreadyReservedException ex) {
        SeatDto errorResponse = new SeatDto(-1, ex.getMessage()); // seatId를 알 수 없음을 의미
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse); // 409 Conflict
    }

    /**
     * 자동 좌석 할당이 불가능한 경우 처리
     */
    @ExceptionHandler(SeatAllReservedException.class)
    public ResponseEntity<Object> handleSeatAllReservedException(SeatAllReservedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    /**
     * 요청한 좌석을 찾을 수 없는 경우 처리
     */
    @ExceptionHandler(SeatsNotAvailableException.class)
    public ResponseEntity<Object> handleSeatsNotAvailableException(SeatsNotAvailableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage()); // 400 Bad Request
    }

    /**
     * 기타 RuntimeException 처리
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Object> handleRuntimeException(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("서버에서 알 수 없는 오류가 발생했습니다: " + ex.getMessage());
    }
}

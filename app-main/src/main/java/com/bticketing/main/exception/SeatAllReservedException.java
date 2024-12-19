package com.bticketing.main.exception;

public class SeatAllReservedException extends RuntimeException{
    public SeatAllReservedException(String message) {
        super(message);
    }

    public SeatAllReservedException(String message, Throwable cause) {
        super(message, cause);
    }

}

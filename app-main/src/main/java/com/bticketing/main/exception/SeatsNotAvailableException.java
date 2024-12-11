package com.bticketing.main.exception;

public class SeatsNotAvailableException extends RuntimeException {

    public SeatsNotAvailableException(String message) {
        super(message);
    }
}

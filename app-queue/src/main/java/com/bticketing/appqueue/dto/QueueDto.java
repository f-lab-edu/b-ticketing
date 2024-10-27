package com.bticketing.appqueue.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class QueueDto {
    private final int userId;
    private final int positionInQueue;
    private final String status;

}

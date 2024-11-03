package com.bticketing.appqueue.dto;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
//현재 불필요코드
public class QueueDto {

    private final int userId;
    private final int positionInQueue;
    private final String status;

}

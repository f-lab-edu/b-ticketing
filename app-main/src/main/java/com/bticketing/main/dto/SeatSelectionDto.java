package com.bticketing.main.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeatSelectionDto {
    private int scheduleId; // 경기 ID, 좌석 배정 시 관련된 경기 일정 식별
    private int sectionId;  // 선택한 좌석 구역 ID
    private int seatCount;  // 자동 좌석 배정 시 좌석 수
    private int[] seatIds;  // 수동 좌석 선택 시 선택한 좌석 ID 리스트
    private boolean vipAccess; // VIP 접근 권한 여부 필드

    public boolean isVipAccess() {
        return vipAccess;
    }
}

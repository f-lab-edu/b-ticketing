package com.bticketing.main.controller;

import com.bticketing.main.dto.SeatSelectionDto;
import com.bticketing.main.enums.UserType;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;


@RestController
@RequestMapping("/seats")
public class SeatSelectionController {

    // 자동 좌석 배정 api
    @PostMapping("/auto-assign")
    public String autoAssignSeats(@RequestBody SeatSelectionDto seatSelectionDto) {
        return "자동 좌석 선택 완료: " + seatSelectionDto.getSeatCount() + "개 좌석 배정";
    }

    // 수동 좌석 선택 api
    @PostMapping("/select")
    public String selectSeats(@RequestBody SeatSelectionDto seatSelectionDto) {
        return "수동 좌석 선택 완료: 좌석 ID " + Arrays.toString(seatSelectionDto.getSeatIds());
    }

    // 선예매 권한 확인 api
    @GetMapping("/vip-access")
    public String checkVIPAccess(@RequestParam UserType userType) {
        if (userType == UserType.VIP) {
            return "선예매 유저입니다.";
        } else {
            return "현재 선예매유저만 접근이 가능합니다.";
        }

    }
}

package com.bticketing.main.controller;

import com.bticketing.main.dto.SeatSelectionDto;
import com.bticketing.main.enums.UserType;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;


@RestController
@RequestMapping("/seats")
public class SeatSelectionController {


    // 자동 좌석 배정 API
    @PostMapping("/auto-assign")
    public SeatSelectionDto autoAssignSeats(@RequestBody SeatSelectionDto seatSelectionDto) {
        // 요청받은 좌석 정보 그대로 반환
        return seatSelectionDto;
    }

    // 수동 좌석 선택 API
    @PostMapping("/select")
    public SeatSelectionDto selectSeats(@RequestBody SeatSelectionDto seatSelectionDto) {
        // 요청받은 좌석 정보 그대로 반환
        return seatSelectionDto;
    }

    // 선예매 권한 확인 api
    // 예매 권한을 자동 좌석 과 수동 좌석에서 추가 검증하는 로직 추가 필요
    // 멤버쉽 가입 및 탈퇴 api 추가 필요
    @GetMapping("/vip-access")
    public SeatSelectionDto checkVIPAccess(@RequestParam UserType userType) {
        SeatSelectionDto seatSelectionDto = new SeatSelectionDto();
        seatSelectionDto.setVipAccess(userType == UserType.VIP); // VIP 접근 가능 여부 설정
        return seatSelectionDto;
    }
    }

